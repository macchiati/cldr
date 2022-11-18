package org.unicode.cldr.tool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Builder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.LocaleDisplayNames.UiListItem;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.ULocale.Minimize;

public class ShowMenuNames {

    public static final Set<ULocale> SKIP = new HashSet(Arrays.asList(
        ULocale.forLanguageTag("sd-Deva"),
        ULocale.forLanguageTag("zh-Hant"),
        ULocale.forLanguageTag("yue-Hans"),
        ULocale.forLanguageTag("uz-Arab"),
        ULocale.forLanguageTag("pa-Arab")));

    /**
     * Choice of display locale
     */
    public enum DisplayLocale {usersLocale, localesLocale, none}

    /**
     * Output data
     */
    public static final class Info {
        private final String secondaryDisplayName;
        private final String locale;
        private final String modifiedLocale;

        public String getSelfDisplayName() {
            return secondaryDisplayName;
        }
        public String getLocale() {
            return locale;
        }
        public Info(String secondaryDisplayName, Locale locale, Locale modifiedLocale) {
            this.secondaryDisplayName = secondaryDisplayName;
            this.locale = locale.toLanguageTag();
            this.modifiedLocale = modifiedLocale.toLanguageTag();
        }
    }

    public static void main(String[] args) {
        Set<Locale> locales;
        if (args.length != 0) {
            List<String> rawLocales = Arrays.asList(args);
            locales = rawLocales
                .stream()
                .map(x -> Locale.forLanguageTag(x))
                .collect(Collectors.toSet());
        } else {
            // filter out equivalent ids
            List<ULocale> rawLocales = Arrays.asList(ULocale.getAvailableLocales());
            locales = rawLocales
                .stream()
                .filter(x -> !SKIP.contains(x))
                .map(x -> minimizeSubtags(x, Minimize.FAVOR_REGION).toLocale())
                .collect(Collectors.toSet());
        }

        Locale usersDisplayLocale = new Locale("da");
        final Collator collator = Collator.getInstance(usersDisplayLocale);
        DisplayLocale primaryChoice = DisplayLocale.usersLocale;
        DisplayLocale secondaryChoice = DisplayLocale.localesLocale;

        // find all languages with variant

        try {
            Map<Locale, Locale> displayLocaleToOriginal = getExpandedLocaleId(locales);


            Map<String, Info> namesToCode = new TreeMap<>(collator);

            for (Entry<Locale, Locale> entry : displayLocaleToOriginal.entrySet()) {
                Locale localeToDisplay = entry.getKey();
                Locale locale = entry.getValue();
                String primary = primaryChoice == DisplayLocale.usersLocale
                    ? localeToDisplay.getDisplayName(usersDisplayLocale)
                        : localeToDisplay.getDisplayName(localeToDisplay);
                String secondary = secondaryChoice == DisplayLocale.none
                    ? null
                        : secondaryChoice == DisplayLocale.usersLocale
                        ? localeToDisplay.getDisplayName(usersDisplayLocale)
                            : localeToDisplay.getDisplayName(localeToDisplay);

                final Info newInfo = new Info(secondary, locale, localeToDisplay);
                Info oldInfo = namesToCode.put(primary, newInfo);

                // check for duplicate names. That won't happen unless the list contains two equivalent locale identifiers.
                if (oldInfo != null) {
                    String msg = "Two different locale identifiers, '" + oldInfo.locale + "' & '" + newInfo.locale
                        + "', have the same display name: "
                        + primary
                        + ". Typically this results from using two equivalent ids, such as 'de' and 'de-DE'.";
                    System.out.println(msg);
                    //throw new IllegalArgumentException(msg);
                }
            }

            for (Entry<String, Info> entry : namesToCode.entrySet()) {
                System.out.println(
                    entry.getKey()
                    + "\t" + entry.getValue().secondaryDisplayName
                    + "\t" + entry.getValue().locale
                    + "\t" + entry.getValue().modifiedLocale);
            }
        } catch (Exception e) {
            System.out.println("New Code fails:" + e.getMessage());
        }

        LocaleDisplayNames danish = LocaleDisplayNames.getInstance(usersDisplayLocale);
        Set<ULocale> uLocaleSet = locales.stream().map(x -> ULocale.forLocale(x)).collect(Collectors.toSet());
        List<UiListItem> uiList = danish.getUiList(uLocaleSet, false, collator);
        System.out.println("\ngetUiList");
        for (UiListItem entry : uiList) {

        System.out.println(
            entry.nameInDisplayLocale
            + "\t" + entry.nameInSelf
            + "\t" + entry.modified.toLanguageTag()
            );
        }

    }

    public static ULocale minimizeSubtags(ULocale x, Minimize minimize) {
        boolean hasScript = x.getScript().isEmpty();
        boolean hasRegion = x.getCountry().isEmpty();
        minimize = hasScript == hasRegion ? minimize : hasScript ? Minimize.FAVOR_SCRIPT : Minimize.FAVOR_REGION;
        return ULocale.minimizeSubtags(x, minimize);
    }

/**
 * Return an mapping from an expanded locale to each of the original locales.
 * An expanded locale is one where the default script or region (or both) is added to be
 * parallel to others with the same locale in the input.
 * <br>For example, if the original input is {"de", "de-AT", "fr"}
 * the resulting mapping is {"de-DE"="de", "de-AT"="de-AT", "fr"="fr"}.
 * The map is unordered, because the eventual order should be
 * <br>(a) sorted by the display forms (eg "German" not "de"), and
 * <br>(b) using the user's locale's sort order.
 * <br>NOTE: an alternative API would be to only return mappings for the locales that change.
 * <br>WARNING: no two locales in the input should be equivalent: that is, "de" is ok, as is "de-DE", but not both!
 */
public static Map<Locale, Locale> getExpandedLocaleId(Iterable<Locale> locales) {
    Map<String, Variant> languageToVariant = new HashMap<>();
    for (Locale locale : locales) {
        String lang = locale.getLanguage();
        String script = locale.getScript();
        String region = locale.getCountry();
        if (!script.isEmpty() || !region.isEmpty()) {
            Variant variant = languageToVariant.get(lang);
            Variant newVariant = Variant.from(variant, script, region);
            if (!Objects.equals(variant, newVariant)) {
                languageToVariant.put(lang, newVariant);
            }
        }
    }

    Map<Locale, Locale> displayLocaleToOriginal = new HashMap<>();
    for (Locale locale : locales) {
        Locale localeToDisplay = locale;
        String lang = locale.getLanguage();
        Variant variant = languageToVariant.get(lang);
        if (variant != null) {
            // Don't create these unless we need them
            // If we need to, we build the locale by first first making a copy, so we get variants, extensions, etc
            Builder localeBuilder = null;
            Locale likely = null;


            if ((variant == Variant.script || variant == Variant.both) && locale.getScript().isEmpty()) {
                if (localeBuilder == null) {
                    localeBuilder = new Locale.Builder().setLocale(locale);
                    likely = ULocale.addLikelySubtags(ULocale.forLocale(locale)).toLocale();
                }
                localeBuilder.setScript(likely.getScript());
            }

            if ((variant == Variant.region || variant == Variant.both) && locale.getCountry().isEmpty()) {
                if (locale.getCountry().isEmpty()) {
                    if (localeBuilder == null) {
                        localeBuilder = new Locale.Builder().setLocale(locale);
                        likely = ULocale.addLikelySubtags(ULocale.forLocale(locale)).toLocale();
                    }
                    localeBuilder.setRegion(likely.getCountry());
                }

            }

            // If we added a script or region, then we reset the locale to display.

            if (localeBuilder != null) {
                localeToDisplay = localeBuilder.build();
            }
        }
        displayLocaleToOriginal.put(localeToDisplay, locale);
    }
    return displayLocaleToOriginal;
}

private enum Variant {
    // do combined enums because ICU can't use Multimap
    script,
    region,
    both;

    static Variant from(Variant variant, String script, String region) {
        if (variant == null) {
            if (!script.isEmpty()) {
                if (!region.isEmpty()) {
                    return both;
                } else { // region empty
                    return Variant.script;
                }
            } else if (!region.isEmpty()) {
                return Variant.region;
            } else { // region not empty
                return null;
            }
        }
        switch(variant) {
        case script:
            if (!region.isEmpty()) {
                return Variant.both;
            }
            break;
        case region:
            if (!script.isEmpty()) {
                return Variant.both;
            }
            break;
        case both:
            break;
        }
        return variant;
    }
}

}
