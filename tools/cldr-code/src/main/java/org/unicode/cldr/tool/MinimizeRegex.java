package org.unicode.cldr.tool;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Output;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.unicode.cldr.util.Timer;

/**
 * Class to minimize certain types of regex. It does not work with open-ended quantifiers like * or
 * +. The regex that is produced requires no backup, so should be faster as well as more compact.
 * (But less readable!)
 *
 * @author markdavis
 */
public class MinimizeRegex {
    /**
     * Sort strings length-first, because (ab|abc) in regex world can stop at the first match, eg
     * "ab".
     */
    private static final Comparator<String> LENGTH_FIRST_COMPARE =
            Comparator.comparingInt(String::length)
                    .reversed()
                    .thenComparing(Comparator.naturalOrder());

    public static void main(String[] args) {
        String defaultArg =
                "(a([bfkmvyz]|ce|d[ay]|gq|in|l[et]|n[np]?|r[nps]?|s[at]?|tj|wa)|b([gm-os]|a[ns]?|e[mz]?|ho|in?|la|rx?|ug|yn)|c([ovy]|ay?|cp|eb?|gg|h[kmopry]?|kb|lc|r[gjklmr]|sw?)|d([evz]|a[krv]?|gr|je|oi|sb|ua|yo|zg)|e([elnos-u]|bu|fi|ka|wo)|f([afjy]|il?|on?|r[cr]?|ur)|fa_AF|g([dlnv]|aa?|ez|il|or|sw|uz?|wi)|h([ertyz]|a[iwx]?|il?|mn|sb|u[pr]?)|i([adgiostu]|b[ab]|kt|lo|nh)|j([av]|bo|go|mc)|k([ijnvy]|a[bcjm]?|bd|cg|de|ea|fo|gp|h[aq]|kj?|ln?|mb?|ok?|pe|r[clu]?|s[bfh]?|um?|wk?)|l([bgntv]|a[dg]?|ez|il?|kt|o[uz]?|rc|sm|u[anosy]?)|m([hklr-ty]|a[dgiks]|df|e[nr]|fe|g[ho]?|i[cn]?|ni?|o[ehs]|u[als]|wl|yv|zn)|n([bglrv]|a[pq]?|ds?|ew?|i[au]|mg|nh?|og?|qo|so|us|yn?)|o([cmrs]|j[bcsw]|ka)|p([lst]|a[gmpu]?|cm|is|qm)|qu|r([mn]|a[pr]|hg|of?|wk?|up?)|s([dgikoqsv]|a[dhqt]?|b[ap]|c[no]?|e[hs]?|h[in]|lh?|m[ns]?|nk?|rn?|tr?|uk?|wb?|yr)|t([akns]|ce|e[mot]?|gx?|ht?|ig?|l[hi]|ok?|pi|rv?|tm?|um|vl|wq|yv?|zm)|u([gkrz]|dm|mb|nd)|v([ei]|ai|un)|w(a[elr]?|o|uu)|x(al|h|og)|y([io]|av|bb|rl|ue)|z(gh|h|un?|xx|za))";
        // defaultArg =
        // "aa|ace|ad[ay]|ain|al[et]|anp?|arp|ast|av|awa|ay|ma[dgik]|mdf|men|mh|mi[cn]|mni|mos|mu[ls]|mwl|myv";
        String regexString = args.length < 1 ? defaultArg : args[0];
        UnicodeSet set = new UnicodeSet(args.length < 2 ? "[:ascii:]" : args[1]);

        System.out.println("default:\n" + defaultArg + "\n");
        Output<Set<String>> flattenedOut = new Output<>();
        String recompressed = compressWith(regexString, set, flattenedOut);
        final String flattenedString = Joiner.on("|").join(flattenedOut.value);
        System.out.println("flattened:\n" + flattenedString + "\n");
        System.out.println("compressed:\n" + recompressed + "\n");

        // try again
        Output<Set<String>> flattenedOut2 = new Output<>();
        String recompressed2 = compressWith(recompressed, set, flattenedOut2);
        if (!flattenedOut.value.equals(flattenedOut2.value)) {
            System.out.println(
                    "flattened doesn't roundtrip:\n"
                            + Joiner.on("|").join(flattenedOut2.value)
                            + "\n");
        }
        if (!recompressed.equals(recompressed2)) {
            System.out.println("recompressed doesn't roundtrip:\n" + recompressed2 + "\n");
        }
        List<String> all = generateAll();
        Set<String> allSet = ImmutableSet.copyOf(all);

        System.out.println("Warmup");
        timeRegex("*source:\t", defaultArg, all, 1);
        timeContains("*contains:\t", allSet, 1);

        System.out.println("\nTime");
        int count = 20;
        timeRegex("source:\t", defaultArg, all, count);
        timeRegex("flattened:\t", flattenedString, all, count);
        timeRegex("recompressed:\t", recompressed, all, count);
        timeContains("contains:\t", allSet, count);
    }

    public static void timeRegex(String message, String regex, List<String> all, int count) {
        Matcher m = Pattern.compile(regex).matcher("");
        timeFunction(message, all, count, s -> m.reset(s).matches());
    }

    public static void timeContains(String message, Set<String> all, int count) {
        timeFunction(message, all, count, s -> all.contains(s));
    }

    public static void timeFunction(
            String message, Collection<String> all, int count, Consumer<? super String> action) {
        Timer t = new Timer();
        t.start();
        for (int i = count; i > 0; --i) {
            for (String s : all) {
                action.accept(s);
            }
        }
        t.stop();
        System.out.println(message + t.getNanoseconds());
    }

    public static List<String> generateAll() {
        List<String> all = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (char i = 'a'; i <= 'z'; ++i) {
            buffer.append(i);
            for (char j = 'a'; j <= 'z'; ++j) {
                buffer.append(j);
                for (char k = 'a'; k <= 'z'; ++k) {
                    buffer.append(k);
                    all.add(buffer.toString());
                    buffer.setLength(buffer.length() - 1);
                }
                buffer.setLength(buffer.length() - 1);
            }
            buffer.setLength(buffer.length() - 1);
        }
        return all;
    }

    public static String compressWith(String regexString, UnicodeSet set) {
        return compressWith(regexString, set, null);
    }

    public static String simplePattern(Collection<String> strings) {
        TreeSet<String> temp = new TreeSet<>(LENGTH_FIRST_COMPARE);
        temp.addAll(strings);
        return Joiner.on("|").join(temp);
    }

    public static String compressWith(
            String regexString, UnicodeSet set, Output<Set<String>> flattenedOut) {
        Set<String> flattened = flatten(Pattern.compile(regexString), "", set);
        String regexString2 = Joiner.on("|").join(flattened);
        Set<String> flattened2 = flatten(Pattern.compile(regexString2), "", set);
        if (!flattened2.equals(flattened)) {
            throw new IllegalArgumentException(
                    "Failed to compress: "
                            + regexString
                            + " using "
                            + set
                            + ", got "
                            + regexString2);
        }

        if (flattenedOut != null) {
            flattenedOut.value = flattened;
        }
        return compressWith(flattened, set);
    }

    /**
     * Does not work with sets of strings containing regex syntax.
     *
     * @param flattened
     * @param set
     * @return
     */
    public static String compressWith(Set<String> flattened, UnicodeSet set) {
        String recompressed = compress(flattened, new Output<Boolean>());
        Set<String> flattened2;
        try {
            flattened2 = flatten(Pattern.compile(recompressed), "", set);
        } catch (PatternSyntaxException e) {
            int loc = e.getIndex();
            if (loc >= 0) {
                recompressed =
                        recompressed.substring(0, loc) + "$$$$$" + recompressed.substring(loc);
            }
            throw new IllegalArgumentException("Failed to parse: " + recompressed, e);
        }
        if (!flattened2.equals(flattened)) {
            throw new IllegalArgumentException(
                    "Failed to compress:\n" + flattened + "\n≠ " + flattened2);
        }
        return "(?>" + recompressed + ")";
    }

    private static String compress(Set<String> flattened, Output<Boolean> isSingle) {
        // make a map from first code points to remainder
        Multimap<Integer, String> firstToRemainder = TreeMultimap.create();
        UnicodeSet results = new UnicodeSet();
        boolean hasEmpty = false;
        for (String s : flattened) {
            if (s.isEmpty()) {
                hasEmpty = true;
                continue;
            }
            int first = s.codePointAt(0);
            firstToRemainder.put(first, s.substring(UCharacter.charCount(first)));
        }
        StringBuilder buf = new StringBuilder();
        for (Entry<Integer, Collection<String>> entry : firstToRemainder.asMap().entrySet()) {
            Set<String> items = (Set<String>) entry.getValue();
            buf.setLength(0);
            buf.appendCodePoint(entry.getKey());
            if (items.size() == 1) {
                buf.append(items.iterator().next());
            } else {
                String sub = compress(items, isSingle);
                if (isSingle.value) {
                    buf.append(sub);
                } else {
                    buf.append('(').append(sub).append(')');
                }
            }
            results.add(buf.toString());
        }
        Set<String> strings = new TreeSet<>(results.strings());
        results.removeAll(strings);
        switch (results.size()) {
            case 0:
                break;
            case 1:
                strings.add(results.iterator().next());
                break;
            default:
                strings.add(results.toPattern(false));
                break;
        }
        switch (strings.size()) {
            case 0:
                throw new IllegalArgumentException();
            case 1:
                isSingle.value = true;
                return strings.iterator().next() + (hasEmpty ? "?+" : "");
            default:
                String result = Joiner.on("|").join(strings);
                if (hasEmpty) {
                    isSingle.value = true;
                    return '(' + result + ")?+";
                }
                isSingle.value = false;
                return result;
        }
    }

    public static TreeSet<String> flatten(Pattern pattern, String prefix, UnicodeSet set) {
        return flatten(pattern.matcher(""), prefix, set, new TreeSet<>(LENGTH_FIRST_COMPARE));
    }

    private static TreeSet<String> flatten(
            Matcher matcher, String prefix, UnicodeSet set, TreeSet<String> results) {
        for (String s : set) {
            String trial = prefix + s;
            matcher.reset(trial);
            boolean matches = matcher.matches();
            if (matches) {
                results.add(trial);
            }
            if (matcher.hitEnd()) {
                flatten(matcher, trial, set, results);
            }
        }
        return results;
    }
}
