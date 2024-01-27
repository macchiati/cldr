package org.unicode.cldr.tool;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.ibm.icu.util.ULocale;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.GrammarInfo;
import org.unicode.cldr.util.GrammarInfo.GrammaticalFeature;
import org.unicode.cldr.util.GrammarInfo.GrammaticalScope;
import org.unicode.cldr.util.GrammarInfo.GrammaticalTarget;
import org.unicode.cldr.util.LanguageGroup;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.Organization;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;

public class ListGrammarInfo {
    public static final CLDRConfig CONFIG = CLDRConfig.getInstance();
    public static final SupplementalDataInfo SDI = CONFIG.getSupplementalDataInfo();
    public static final CLDRFile english = CONFIG.getEnglish();

    public static void main(String[] args) {
        Set<String> locales = GrammarInfo.getGrammarLocales();
        LanguageTagParser ltp = new LanguageTagParser();
        Set<String> sortedGenderLocales = new TreeSet<>();
        Set<String> sortedCaseLocales = new TreeSet<>();
        Set<String> sortedBothLocales = new TreeSet<>();

        for (String locale : locales) {
            if (locale.equals("root")) {
                continue;
            }
            ltp.set(locale);
            String region = ltp.getRegion();
            if (!region.isEmpty()) {
                continue;
            }
            GrammarInfo grammarInfo = SDI.getGrammarInfo(locale, true);
            if (grammarInfo == null || !grammarInfo.hasInfo(GrammaticalTarget.nominal)) {
                continue;
            }
            // CLDRFile cldrFile = factory.make(locale, true);

            Collection<String> genders =
                    grammarInfo.get(
                            GrammaticalTarget.nominal,
                            GrammaticalFeature.grammaticalGender,
                            GrammaticalScope.units);
            Collection<String> rawCases =
                    grammarInfo.get(
                            GrammaticalTarget.nominal,
                            GrammaticalFeature.grammaticalCase,
                            GrammaticalScope.units);

            boolean hasGender = genders != null && genders.size() > 1;
            boolean hasCase = rawCases != null && rawCases.size() > 1;

            if (hasGender) {
                if (hasCase) {
                    sortedBothLocales.add(format(locale, genders, rawCases));
                } else {
                    sortedGenderLocales.add(format(locale, genders));
                }
            } else if (hasCase) {
                sortedCaseLocales.add(format(locale, rawCases));
            }
        }
        System.out.println("Gender\t" + Joiner.on(", ").join(sortedGenderLocales));
        System.out.println("Case\t" + Joiner.on(", ").join(sortedCaseLocales));
        System.out.println("Gender & Case\t" + Joiner.on(", ").join(sortedBothLocales));

        // now raw data:
        Set<String> mainCLDR =
                ImmutableSortedSet.copyOf(
                        Sets.union(
                                SDI.hasGrammarInfo(),
                                Sets.difference(
                                        StandardCodes.make()
                                                .getLocaleCoverageLocales(
                                                        Organization.cldr,
                                                        Sets.immutableEnumSet(Level.MODERN)),
                                        StandardCodes.make()
                                                .getLocaleCoverageLocales(Organization.special))));
        Multimap<LanguageGroup, String> sorted = LinkedHashMultimap.create();
        for (String locale : mainCLDR) {
            if (locale.contains("_")) {
                continue; // skip
            }
            LanguageGroup languageGroup = LanguageGroup.get(new ULocale(locale));
            GrammarInfo grammarInfo = SDI.getRawGrammarInfo(locale);
            if (grammarInfo == null) {
                grammarInfo = SDI.getGrammarInfo(locale);
                if (grammarInfo == null) { // only show ones without parent info
                    sorted.put(languageGroup, locale + "\t" + "MISSING");
                }
                continue;
            }
            boolean haveItem = false;
            for (GrammaticalTarget target : GrammaticalTarget.values()) {
                for (GrammaticalFeature feature : GrammaticalFeature.values()) {
                    Collection<String> generalValues = null;
                    for (GrammaticalScope scope : GrammaticalScope.values()) {
                        Collection<String> values = grammarInfo.get(target, feature, scope);
                        if (scope == GrammaticalScope.general) {
                            generalValues = values;
                        }
                        // empty set means none, null means not present.
                        if (values != null) {
                            String joined = Joiner.on(' ').join(values);
                            if (scope != GrammaticalScope.general
                                    && Objects.equal(generalValues, values)) {
                                joined = "🟰general scope";
                            }
                            sorted.put(
                                    languageGroup,
                                    locale
                                            + "\t"
                                            + target
                                            + "\t"
                                            + feature
                                            + "\t"
                                            + scope
                                            + "\t"
                                            + (joined.isEmpty() ? "NONE" : joined));
                            haveItem = true;
                        }
                    }
                }
            }
            if (!haveItem) {
                sorted.put(
                        languageGroup,
                        locale + "\t" + LanguageGroup.get(new ULocale(locale)) + "\t" + "NONE");
            }
        }
        for (LanguageGroup languageGroup : LanguageGroup.values()) {
            for (String value : sorted.get(languageGroup)) {
                System.out.println(languageGroup + "\t" + value);
            }
        }
    }

    private static String format(
            String locale, Collection<String> genders, Collection<String> rawCases) {
        return english.getName(locale)
                + " ("
                + locale
                + "/"
                + genders.size()
                + "×"
                + rawCases.size()
                + ")";
    }

    public static String format(String locale, Collection<String> genders) {
        return english.getName(locale) + " (" + locale + "/" + genders.size() + ")";
    }
}
