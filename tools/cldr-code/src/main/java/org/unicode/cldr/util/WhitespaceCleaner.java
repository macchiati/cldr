package org.unicode.cldr.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSet.SpanCondition;

/**
 * Coalesces any sequence of 1 or more whitespace characters, replacing by
 * a single whitespace character, chosen according to priorities supplied
 * in the constructor. Immutable
 */
final class WhitespaceCleaner implements Function<String, String> {

    public static final String SP = "\u0020";
    public static final String NBSP = "\u00A0";
    public static final String TSP = "\u2009";
    public static final String NBTSP = "\u202F";
    public static final String ISP = "\u3000";

    static final UnicodeSet XWHITESPACE = new UnicodeSet("\\p{whitespace}").freeze();

    private final Map<Integer, Integer> priorities;

    // we could make this set be a parameter if there were any reason to do so.
    // we'd need a bit of enhancement to have this work for fixing \n.
    private final int fallbackCharacter;

    /**
     * The fallback character is the first one in the first string.
     * The priorities are set for all the characters in each string according to
     * the order of the strings. Example: WhitespaceCoalescer("xy", "z", "vw")
     * <ul><li>sets both x and y to the highest priority (0),
     * </li><li>z to the next priority (1),
     * </li><li>and v and w to the next priority (2).
     * </li><li>Any other whitespace character (\\p{whitespace}) is removed,
     * and if there are no remaining characters the first (x) is used.
     * @param priorities
     */
    public WhitespaceCleaner(String... priorities) {
        this(getPriorities(priorities), priorities[0].charAt(0));
    }

    private static Map<Integer, Integer> getPriorities(String... priorities) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (String s : priorities) {
            for (int i = 0; i < s.length(); ++i) {
                result.put((int)s.charAt(i), i);
            }
        }
        return result;
    }

    private WhitespaceCleaner(Map<Integer, Integer> priorities, int fallbackCharacter) {
        if (!priorities.containsKey(fallbackCharacter)) {
            throw new IllegalArgumentException("Fallback character must be in priorities map.");
        }
        this.priorities = ImmutableMap.copyOf(priorities);
        this.fallbackCharacter = fallbackCharacter;
    }

    /**
     * Replaces a sequence of whitespace characters according to priorities
     * supplied in the constructor.
     * @param source
     * @param priorities
     * @return
     */
    @Override
    public String apply(String source) {
        // could be optimized if there is only one possible character, etc.
        StringBuilder result = null; // allocate if needed
        int length = source.length();
        int whitespaceLimit = 0;
        int copyLimit = 0;
        while (true) {
            int firstWhitespace = XWHITESPACE.span(source, whitespaceLimit, SpanCondition.NOT_CONTAINED);
            if (firstWhitespace == length) {
                return result == null ? source : result.append(source, copyLimit, length).toString();
            }
            whitespaceLimit = XWHITESPACE.span(source, firstWhitespace, SpanCondition.SIMPLE);
            // whitespace characters are all BMP, so we can do this
            // look for the best character
            int bestPriority = Integer.MAX_VALUE;
            int bestCharacter = -1;
            for (int i = firstWhitespace; i < whitespaceLimit; ++i) {
                int cp = source.charAt(i);
                Integer priority = priorities.get(cp);
                if (priority != null && priority < bestPriority) {
                    bestPriority = priority;
                    bestCharacter = cp;
                }
            }
            if (bestCharacter < 0) { // we don't have a permitted whitespace
                bestCharacter = fallbackCharacter;
            } else if (whitespaceLimit - firstWhitespace == 1) {
                continue;  // if we spanned 1 character, then we don't have to do anything
            }
            // allocate result if not allocated
            if (result == null) {
                result = new StringBuilder();
                copyLimit = 0;
            }
            // append from the last point we copied up to the start of the whitespace first
            result.append(source, copyLimit, firstWhitespace);
            // then the best character
            result.appendCodePoint(bestCharacter);
            // remember where we copied up to.
            copyLimit = whitespaceLimit;
        }
    }

    // TODO move to unit tests

    public static void main(String[] args) {
        WhitespaceCleaner wsc = null;
        String[][] tests = {
            {"SET", NBTSP, NBSP, TSP, SP}, // successively lower priority
            {"a  ", "a "},
            {"  a", " a"},
            {"a  b", "a b"},
            {"a" + ISP + SP + "b", "a b"},
            {"a" + ISP + NBTSP + "b", "a" + NBTSP + "b"},
            {"a" + ISP + "b", "a" + NBTSP + "b"},
            {"SET", NBSP+NBTSP+SP+TSP}, // same priority, NBSP is default
            {"a  ", "a "},
            {"  a", " a"},
            {"a" + ISP + SP + "b", "a b"},
            {"a" + ISP + NBTSP + "b", "a" + NBTSP + "b"},
            {"a" + ISP + "b", "a" + NBSP + "b"},
            };
        int i = 0;
        for (String[] row : tests) {
            ++i;
            if (row[0].equals("SET")) {
                wsc = new WhitespaceCleaner(Arrays.copyOfRange(row, 1, row.length));
                continue;
            }
            final String actual = wsc.apply(row[0]);
            assertEquals(i + ") «" + row[0] + "» " + Utility.hex(row[0]), Utility.hex(row[1]), Utility.hex(actual));
        }
    }

    public static boolean assertEquals(String message, String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            System.out.println("\nSource:  \t" + message);
            System.out.println("Expected:\t" + expected + "\nActual:  \t" + actual);
            return false;
        }
        return true;
    }
}