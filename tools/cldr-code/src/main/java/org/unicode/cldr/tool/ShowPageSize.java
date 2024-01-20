package org.unicode.cldr.tool;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.LocaleData;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.Organization;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.PageId;
import org.unicode.cldr.util.PathHeader.SectionId;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PopulationData;

public class ShowPageSize {

    static Comparator<Pair<SectionId, PageId>> comparator =
            new Comparator<>() {
                @Override
                public int compare(Pair<SectionId, PageId> o1, Pair<SectionId, PageId> o2) {
                    return ComparisonChain.start()
                            .compare(o1.getFirst(), o2.getFirst())
                            .compare(o1.getSecond(), o2.getSecond())
                            .result();
                }
            };

    public static void main(String[] args) {
        showProxyFrequency();
    }

    public static void pageSize() {
        Factory factory = CLDRConfig.getInstance().getCommonAndSeedAndMainAndAnnotationsFactory();
        // "en", "cs", "ar", "pl"
        List<String> locales =
                StandardCodes.make()
                        .getLocaleCoverageLocales(Organization.cldr, ImmutableSet.of(Level.MODERN))
                        .stream()
                        .filter(x -> CLDRLocale.getInstance(x).getCountry().isEmpty())
                        .collect(Collectors.toUnmodifiableList());
        List<Counter<Pair<SectionId, PageId>>> counters = new ArrayList<>();
        for (String locale : locales) {
            CLDRFile cldrFile = factory.make(locale, false);
            PathHeader.Factory phf = PathHeader.getFactory();
            Counter<Pair<SectionId, PageId>> c = new Counter<>();
            counters.add(c);
            for (String path : cldrFile) {
                PathHeader ph = phf.fromPath(path);
                c.add(Pair.of(ph.getSectionId(), ph.getPageId()), 1);
            }
        }
        Set<Pair<SectionId, PageId>> sorted = new TreeSet<>();
        for (Counter<Pair<SectionId, PageId>> counter : counters) {
            sorted.addAll(counter.keySet());
        }
        int i = 0;
        System.out.print("Order" + "\t" + "Section" + "\t" + "Page");
        for (String c : locales) {
            System.out.print("\t" + c);
        }
        System.out.println();

        for (Pair<SectionId, PageId> entry : sorted) {
            System.out.print(++i + "\t" + entry.getFirst() + "\t" + entry.getSecond());
            for (Counter<Pair<SectionId, PageId>> c : counters) {
                System.out.print("\t" + c.get(entry));
            }
            System.out.println();
        }
    }

    public static void showProxyFrequency() {
        Counter<Integer> characterToPopulation = new Counter<>();

        Set<ULocale> availableLocales = new TreeSet<>();
        availableLocales.addAll(Arrays.asList(ULocale.getAvailableLocales()));

        for (ULocale ulocale : availableLocales) {
            if (!ulocale.getCountry().isEmpty() || !ulocale.getVariant().isEmpty()) {
                continue;
                // we want to skip cases where characters are in the parent locale, but there is no
                // ULocale parentLocale = ulocale.getParent();
            }
            CLDRLocale parent = CLDRLocale.getInstance(ulocale).getParent();
            if (availableLocales.contains(parent.toULocale())) {
                continue;
            }
            PopulationData pop =
                    SupplementalDataInfo.getInstance()
                            .getBaseLanguagePopulationData(ulocale.toString());
            long literate = pop == null ? 0 : (long) pop.getLiteratePopulation();
            System.out.println(ulocale + "\t" + literate);
            if (literate < 1000) { // small populations have less accuracy
                continue;
            }
            UnicodeSet localeExemplarSet = new UnicodeSet();
            for (int exemplarType :
                    Arrays.asList(LocaleData.ES_STANDARD, LocaleData.ES_PUNCTUATION)) {
                UnicodeSet exemplarSet = LocaleData.getExemplarSet(ulocale, 0, exemplarType);
                localeExemplarSet.addAll(exemplarSet);
            }
            // flatten
            final Collection<String> strings = localeExemplarSet.strings();
            if (!strings.isEmpty()) {
                UnicodeSet flattened = new UnicodeSet();
                for (String s : strings) {
                    int cp;
                    for (int i = 0; i < s.length(); i += Character.charCount(cp)) {
                        cp = s.codePointAt(i);
                        flattened.add(cp);
                    }
                }
                localeExemplarSet.removeAll(ImmutableSet.copyOf(strings));
                localeExemplarSet.addAll(flattened);
            }
            for (String s : localeExemplarSet) {
                characterToPopulation.add(s.codePointAt(0), literate);
            }
        }

        // print data

        UnicodeSet items = new UnicodeSet();
        long lastCount = Long.MIN_VALUE;
        for (R2<Long, Integer> i : characterToPopulation.getEntrySetSortedByCount(false, null)) {
            long currentCount = i.get0();
            if (currentCount != lastCount) {
                if (lastCount != Long.MIN_VALUE) {
                    System.out.println(
                            lastCount + "\t" + items.size() + "\t" + items.toPattern(false));
                }
                items.clear();
                lastCount = currentCount;
            }
            items.add(i.get1());
        }
        System.out.println(lastCount + "\t" + items.size() + "\t" + items.toPattern(false));
    }
}
