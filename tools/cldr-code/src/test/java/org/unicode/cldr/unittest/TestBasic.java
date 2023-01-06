package org.unicode.cldr.unittest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.tool.CldrVersion;
import org.unicode.cldr.tool.LikelySubtags;
import org.unicode.cldr.util.Builder;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.ExemplarType;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRFile.WinningChoice;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.ChainedMap;
import org.unicode.cldr.util.ChainedMap.M4;
import org.unicode.cldr.util.CharacterFallbacks;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.DiscreteComparator;
import org.unicode.cldr.util.DiscreteComparator.Ordering;
import org.unicode.cldr.util.DtdData;
import org.unicode.cldr.util.DtdData.Attribute;
import org.unicode.cldr.util.DtdData.Element;
import org.unicode.cldr.util.DtdData.ElementType;
import org.unicode.cldr.util.DtdType;
import org.unicode.cldr.util.ElementAttributeInfo;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.InputStreamFactory;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathUtilities;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.impl.Relation;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Row.R3;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.ULocale;

public class TestBasic extends TestFmwkPlus {

    private static final boolean DEBUG = false;

    static CLDRConfig testInfo = CLDRConfig.getInstance();

    private static final SupplementalDataInfo SUPPLEMENTAL_DATA_INFO = testInfo
        .getSupplementalDataInfo();

    private static final ImmutableSet<Pair<String, String>> knownElementExceptions = ImmutableSet.of(
        Pair.of("ldml", "usesMetazone"),
        Pair.of("ldmlICU", "usesMetazone"));

    private static final ImmutableSet<Pair<String, String>> knownAttributeExceptions = ImmutableSet.of(
        Pair.of("ldml", "version"),
        Pair.of("supplementalData", "version"),
        Pair.of("ldmlICU", "version"),
        Pair.of("layout", "standard"),
        Pair.of("currency", "id"),      // for v1.1.1
        Pair.of("monthNames", "type"),  // for v1.1.1
        Pair.of("alias", "type")        // for v1.1.1
        );

    private static final ImmutableSet<Pair<String, String>> knownChildExceptions = ImmutableSet.of(
        Pair.of("abbreviationFallback", "special"),
        Pair.of("inList", "special"),
        Pair.of("preferenceOrdering", "special"));

    /**
     * Simple test that loads each file in the cldr directory, thus verifying
     * that the DTD works, and also checks that the PrettyPaths work.
     *
     * @author markdavis
     */

    public static void main(String[] args) {
        new TestBasic().run(args);
    }

    private static final ImmutableSet<String> skipAttributes = ImmutableSet.of(
        "alt", "draft", "references");

    private final ImmutableSet<String> eightPointLocales = ImmutableSet.of(
        "ar", "ca", "cs", "da", "de", "el", "es", "fi", "fr", "he", "hi", "hr", "hu", "id",
        "it", "ja", "ko", "lt", "lv", "nl", "no", "pl", "pt", "pt_PT", "ro", "ru", "sk", "sl", "sr", "sv",
        "th", "tr", "uk", "vi", "zh", "zh_Hant");

    // private final boolean showForceZoom = Utility.getProperty("forcezoom",
    // false);

    private final boolean resolved = CldrUtility.getProperty("resolved", false);

    private final Exception[] internalException = new Exception[1];

    public void TestDtds() throws IOException {
        Relation<Row.R2<DtdType, String>, String> foundAttributes = Relation
            .of(new TreeMap<Row.R2<DtdType, String>, Set<String>>(),
                TreeSet.class);
        final CLDRConfig config = CLDRConfig.getInstance();
        final File basedir = config.getCldrBaseDirectory();
        List<TimingInfo> data = new ArrayList<>();

        for (String subdir : CLDRConfig.getCLDRDataDirectories()) {
            checkDtds(new File(basedir, subdir), 0, foundAttributes, data);
        }
        if (foundAttributes.size() > 0) {
            showFoundElements(foundAttributes);
        }
        if (isVerbose()) {
            long totalBytes = 0;
            long totalNanos = 0;
            for (TimingInfo i : data) {
                long length = i.file.length();
                totalBytes += length;
                totalNanos += i.nanos;
                logln(i.nanos + "\t" + length + "\t" + i.file);
            }
            logln(totalNanos + "\t" + totalBytes);
        }
    }

    private void checkDtds(File directoryFile, int level,
        Relation<R2<DtdType, String>, String> foundAttributes,
        List<TimingInfo> data) throws IOException {
        boolean deepCheck = getInclusion() >= 10;
        File[] listFiles = directoryFile.listFiles();
        String normalizedPath = PathUtilities.getNormalizedPathString(directoryFile);
        String indent = Utility.repeat("\t", level);
        if (listFiles == null) {
            throw new IllegalArgumentException(indent + "Empty directory: "
                + normalizedPath);
        }
        logln("Checking files for DTD errors in: " + indent + normalizedPath);
        for (File fileName : listFiles) {
            String name = fileName.getName();
            if (CLDRConfig.isJunkFile(name)) {
                continue;
            } else if (fileName.isDirectory()) {
                checkDtds(fileName, level + 1, foundAttributes, data);
            } else if (name.endsWith(".xml")) {
                data.add(check(fileName));
                if (deepCheck // takes too long to do all the time
                    ) {
                    CLDRFile cldrfile = CLDRFile.loadFromFile(fileName, "temp",
                        DraftStatus.unconfirmed);
                    for (String xpath : cldrfile) {
                        String fullPath = cldrfile.getFullXPath(xpath);
                        if (fullPath == null) {
                            fullPath = cldrfile.getFullXPath(xpath);
                            assertNotNull("", fullPath);
                            continue;
                        }
                        XPathParts parts = XPathParts
                            .getFrozenInstance(fullPath);
                        DtdType type = parts.getDtdData().dtdType;
                        for (int i = 0; i < parts.size(); ++i) {
                            String element = parts.getElement(i);
                            R2<DtdType, String> typeElement = Row.of(type,
                                element);
                            if (parts.getAttributeCount(i) == 0) {
                                foundAttributes.put(typeElement, "NONE");
                            } else {
                                for (String attribute : parts
                                    .getAttributeKeys(i)) {
                                    foundAttributes.put(typeElement, attribute);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void showFoundElements(
        Relation<Row.R2<DtdType, String>, String> foundAttributes) {
        Relation<Row.R2<DtdType, String>, String> theoryAttributes = Relation
            .of(new TreeMap<Row.R2<DtdType, String>, Set<String>>(),
                TreeSet.class);
        for (DtdType type : DtdType.values()) {
            DtdData dtdData = DtdData.getInstance(type);
            for (Element element : dtdData.getElementFromName().values()) {
                String name = element.getName();
                Set<Attribute> attributes = element.getAttributes().keySet();
                R2<DtdType, String> typeElement = Row.of(type, name);
                if (attributes.isEmpty()) {
                    theoryAttributes.put(typeElement, "NONE");
                } else {
                    for (Attribute attribute : attributes) {
                        theoryAttributes.put(typeElement, attribute.name);
                    }
                }
            }
        }
        Relation<String, R3<Boolean, DtdType, String>> attributesToTypeElementUsed = Relation
            .of(new TreeMap<String, Set<R3<Boolean, DtdType, String>>>(),
                LinkedHashSet.class);

        for (Entry<R2<DtdType, String>, Set<String>> s : theoryAttributes
            .keyValuesSet()) {
            R2<DtdType, String> typeElement = s.getKey();
            Set<String> theoryAttributeSet = s.getValue();
            DtdType type = typeElement.get0();
            String element = typeElement.get1();
            if (element.equals("ANY") || element.equals("#PCDATA")) {
                continue;
            }
            boolean deprecatedElement = SUPPLEMENTAL_DATA_INFO.isDeprecated(
                type, element, "*", "*");
            String header = type + "\t" + element + "\t"
                + (deprecatedElement ? "X" : "") + "\t";
            Set<String> usedAttributes = foundAttributes.get(typeElement);
            Set<String> unusedAttributes = new LinkedHashSet<>(
                theoryAttributeSet);
            if (usedAttributes == null) {
                logln(header
                    + "<NOT-FOUND>\t\t"
                    + siftDeprecated(type, element, unusedAttributes,
                        attributesToTypeElementUsed, false));
                continue;
            }
            unusedAttributes.removeAll(usedAttributes);
            logln(header
                + siftDeprecated(type, element, usedAttributes,
                    attributesToTypeElementUsed, true)
                + "\t"
                + siftDeprecated(type, element, unusedAttributes,
                    attributesToTypeElementUsed, false));
        }

        logln("Undeprecated Attributes\t");
        for (Entry<String, R3<Boolean, DtdType, String>> s : attributesToTypeElementUsed
            .keyValueSet()) {
            R3<Boolean, DtdType, String> typeElementUsed = s.getValue();
            logln(s.getKey() + "\t" + typeElementUsed.get0()
            + "\t" + typeElementUsed.get1() + "\t"
            + typeElementUsed.get2());
        }
    }

    private String siftDeprecated(
        DtdType type,
        String element,
        Set<String> attributeSet,
        Relation<String, R3<Boolean, DtdType, String>> attributesToTypeElementUsed,
        boolean used) {
        StringBuilder b = new StringBuilder();
        StringBuilder bdep = new StringBuilder();
        for (String attribute : attributeSet) {
            String attributeName = "«"
                + attribute
                + (!"NONE".equals(attribute) && CLDRFile.isDistinguishing(type, element, attribute) ? "*"
                    : "")
                + "»";
            if (!"NONE".equals(attribute) && SUPPLEMENTAL_DATA_INFO.isDeprecated(type, element, attribute,
                "*")) {
                if (bdep.length() != 0) {
                    bdep.append(" ");
                }
                bdep.append(attributeName);
            } else {
                if (b.length() != 0) {
                    b.append(" ");
                }
                b.append(attributeName);
                if (!"NONE".equals(attribute)) {
                    attributesToTypeElementUsed.put(attribute,
                        Row.of(used, type, element));
                }
            }
        }
        return b.toString() + "\t" + bdep.toString();
    }

    class MyErrorHandler implements ErrorHandler {
        @Override
        public void error(SAXParseException exception) throws SAXException {
            errln("error: " + XMLFileReader.showSAX(exception));
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            errln("fatalError: " + XMLFileReader.showSAX(exception));
            throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            errln("warning: " + XMLFileReader.showSAX(exception));
            throw exception;
        }
    }

    private class TimingInfo {
        File file;
        long nanos;
    }

    public TimingInfo check(File systemID) {
        long start = System.nanoTime();
        try (InputStream fis = InputStreamFactory.createInputStream(systemID)) {
            // FileInputStream fis = new FileInputStream(systemID);
            XMLReader xmlReader = XMLFileReader.createXMLReader(true);
            xmlReader.setErrorHandler(new MyErrorHandler());
            InputSource is = new InputSource(fis);
            is.setSystemId(systemID.toString());
            xmlReader.parse(is);
            // fis.close();
        } catch (SAXException | IOException e) {
            errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t"
                + e.getMessage());
        }
        // catch (SAXParseException e) {
        // errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t" +
        // e.getMessage());
        // } catch (IOException e) {
        // errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t" +
        // e.getMessage());
        // }
        TimingInfo timingInfo = new TimingInfo();
        timingInfo.nanos = System.nanoTime() - start;
        timingInfo.file = systemID;
        return timingInfo;
    }

    public void TestCurrencyFallback() {
        Factory cldrFactory = testInfo.getCldrFactory();
        Set<String> currencies = StandardCodes.make().getAvailableCodes(
            "currency");

        final UnicodeSet CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS = new UnicodeSet(
            "[[:sc:]-[\\u0000-\\u00FF]]").freeze();

        CharacterFallbacks fallbacks = CharacterFallbacks.make();

        for (String locale : cldrFactory.getAvailable()) {
            if (!StandardCodes.isLocaleAtLeastBasic(locale)) {
                continue;
            }
            CLDRFile file = testInfo.getCLDRFile(locale, false);
            if (file.isNonInheriting())
                continue;

            final UnicodeSet OK_CURRENCY_FALLBACK = new UnicodeSet(
                "[\\u0000-\\u00FF]").addAll(safeExemplars(file, ""))
                .addAll(safeExemplars(file, "auxiliary"))
                .freeze();
            UnicodeSet badSoFar = new UnicodeSet();

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.endsWith("/alias")) {
                    continue;
                }
                String value = file.getStringValue(path);

                // check for special characters
                if (CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS.containsSome(value)) {
                    XPathParts parts = XPathParts.getFrozenInstance(path);
                    if (!parts.getElement(-1).equals("symbol")) {
                        continue;
                    }
                    // We don't care about fallbacks for narrow currency symbols
                    if ("narrow".equals(parts.getAttributeValue(-1, "alt"))) {
                        continue;
                    }
                    String currencyType = parts.getAttributeValue(-2, "type");

                    UnicodeSet fishy = new UnicodeSet().addAll(value)
                        .retainAll(CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS)
                        .removeAll(badSoFar);
                    for (UnicodeSetIterator it2 = new UnicodeSetIterator(fishy); it2
                        .next();) {
                        final int fishyCodepoint = it2.codepoint;
                        List<String> fallbackList = fallbacks
                            .getSubstitutes(fishyCodepoint);

                        String nfkc = Normalizer.normalize(fishyCodepoint, Normalizer.NFKC);
                        if (!nfkc.equals(UTF16.valueOf(fishyCodepoint))) {
                            if (fallbackList == null) {
                                fallbackList = new ArrayList<>();
                            } else {
                                fallbackList = new ArrayList<>(
                                    fallbackList); // writable
                            }
                            fallbackList.add(nfkc);
                        }
                        // later test for all Latin-1
                        if (fallbackList == null) {
                            errln("Locale:\t" + locale
                                + ";\tCharacter with no fallback:\t"
                                + it2.getString() + "\t"
                                + UCharacter.getName(fishyCodepoint));
                            badSoFar.add(fishyCodepoint);
                        } else {
                            String fallback = null;
                            for (String fb : fallbackList) {
                                if (OK_CURRENCY_FALLBACK.containsAll(fb)) {
                                    if (!fb.equals(currencyType)
                                        && currencies.contains(fb)) {
                                        errln("Locale:\t"
                                            + locale
                                            + ";\tCurrency:\t"
                                            + currencyType
                                            + ";\tFallback converts to different code!:\t"
                                            + fb
                                            + "\t"
                                            + it2.getString()
                                            + "\t"
                                            + UCharacter
                                            .getName(fishyCodepoint));
                                    }
                                    if (fallback == null) {
                                        fallback = fb;
                                    }
                                }
                            }
                            if (fallback == null) {
                                errln("Locale:\t"
                                    + locale
                                    + ";\tCharacter with no good fallback (exemplars+Latin1):\t"
                                    + it2.getString() + "\t"
                                    + UCharacter.getName(fishyCodepoint));
                                badSoFar.add(fishyCodepoint);
                            } else {
                                logln("Locale:\t" + locale
                                    + ";\tCharacter with good fallback:\t"
                                    + it2.getString() + " "
                                    + UCharacter.getName(fishyCodepoint)
                                    + " => " + fallback);
                                // badSoFar.add(fishyCodepoint);
                            }
                        }
                    }
                }
            }
        }
    }

    public void TestAbstractPaths() {
        Factory cldrFactory = testInfo.getCldrFactory();
        CLDRFile english = testInfo.getEnglish();
        Map<String, Counter<Level>> abstactPaths = new TreeMap<>();
        RegexTransform abstractPathTransform = new RegexTransform(
            RegexTransform.Processing.ONE_PASS).add("//ldml/", "")
            .add("\\[@alt=\"[^\"]*\"\\]", "").add("=\"[^\"]*\"", "=\"*\"")
            .add("([^]])\\[", "$1\t[").add("([^]])/", "$1\t/")
            .add("/", "\t");

        for (String locale : getInclusion() <= 5 ? eightPointLocales : cldrFactory.getAvailable()) {
            CLDRFile file = testInfo.getCLDRFile(locale, resolved);
            if (file.isNonInheriting())
                continue;
            logln(locale + "\t-\t" + english.getName(locale));

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.endsWith("/alias")) {
                    continue;
                }
                // collect abstracted paths
                String abstractPath = abstractPathTransform.transform(path);
                Level level = SUPPLEMENTAL_DATA_INFO.getCoverageLevel(path,
                    locale);
                if (level == Level.OPTIONAL) {
                    level = Level.COMPREHENSIVE;
                }
                Counter<Level> row = abstactPaths.get(abstractPath);
                if (row == null) {
                    abstactPaths.put(abstractPath, row = new Counter<>());
                }
                row.add(level, 1);
            }
        }
        logln(CldrUtility.LINE_SEPARATOR + "Abstract Paths");
        for (Entry<String, Counter<Level>> pathInfo : abstactPaths.entrySet()) {
            String path = pathInfo.getKey();
            Counter<Level> counter = pathInfo.getValue();
            logln(counter.getTotal() + "\t" + getCoverage(counter) + "\t"
                + path);
        }
    }

    private CharSequence getCoverage(Counter<Level> counter) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Level level : counter.getKeysetSortedByKey()) {
            if (first) {
                first = false;
            } else {
                result.append(' ');
            }
            result.append("L").append(level.ordinal()).append("=")
            .append(counter.get(level));
        }
        return result;
    }

    // public void TestCLDRFileCache() {
    // long start = System.nanoTime();
    // Factory cldrFactory = testInfo.getCldrFactory();
    // String unusualLocale = "hi";
    // CLDRFile file = cldrFactory.make(unusualLocale, true);
    // long afterOne = System.nanoTime();
    // logln("First: " + (afterOne-start));
    // CLDRFile file2 = cldrFactory.make(unusualLocale, true);
    // long afterTwo = System.nanoTime();
    // logln("Second: " + (afterTwo-afterOne));
    // }
    //
    public void TestPaths() {
        Relation<String, String> distinguishing = Relation.of(
            new TreeMap<String, Set<String>>(), TreeSet.class);
        Relation<String, String> nonDistinguishing = Relation.of(
            new TreeMap<String, Set<String>>(), TreeSet.class);
        Factory cldrFactory = testInfo.getCldrFactory();
        CLDRFile english = testInfo.getEnglish();

        Relation<String, String> pathToLocale = Relation.of(
            new TreeMap<String, Set<String>>(CLDRFile
                .getComparator(DtdType.ldml)),
            TreeSet.class, null);
        Set<String> localesToTest = getInclusion() <= 5 ? eightPointLocales : cldrFactory.getAvailable();
        for (String locale : localesToTest) {
            CLDRFile file = testInfo.getCLDRFile(locale, resolved);
            DtdType dtdType = null;
            if (file.isNonInheriting())
                continue;
            DisplayAndInputProcessor displayAndInputProcessor = new DisplayAndInputProcessor(
                file, false);

            logln(locale + "\t-\t" + english.getName(locale));

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (dtdType == null) {
                    dtdType = DtdType.fromPath(path);
                }

                if (path.endsWith("/alias")) {
                    continue;
                }
                String value = file.getStringValue(path);
                if (value == null) {
                    throw new IllegalArgumentException(locale
                        + "\tError: in null value at " + path);
                }

                String displayValue = displayAndInputProcessor
                    .processForDisplay(path, value);
                if (!displayValue.equals(value)) {
                    logln("\t"
                        + locale
                        + "\tdisplayAndInputProcessor changes display value <"
                        + value + ">\t=>\t<" + displayValue + ">\t\t"
                        + path);
                }
                String inputValue = displayAndInputProcessor.processInput(path,
                    value, internalException);
                if (internalException[0] != null) {
                    errln("\t" + locale
                        + "\tdisplayAndInputProcessor internal error <"
                        + value + ">\t=>\t<" + inputValue + ">\t\t" + path);
                    internalException[0].printStackTrace(System.out);
                }
                if (isVerbose() && !inputValue.equals(value)) {
                    displayAndInputProcessor.processInput(path, value,
                        internalException); // for
                    // debugging
                    logln("\t"
                        + locale
                        + "\tdisplayAndInputProcessor changes input value <"
                        + value + ">\t=>\t<" + inputValue + ">\t\t" + path);
                }

                pathToLocale.put(path, locale);

                // also check for non-distinguishing attributes
                if (path.contains("/identity"))
                    continue;

                String fullPath = file.getFullXPath(path);
                XPathParts parts = XPathParts.getFrozenInstance(fullPath);
                for (int i = 0; i < parts.size(); ++i) {
                    if (parts.getAttributeCount(i) == 0) {
                        continue;
                    }
                    String element = parts.getElement(i);
                    for (String attribute : parts.getAttributeKeys(i)) {
                        if (skipAttributes.contains(attribute))
                            continue;
                        if (CLDRFile.isDistinguishing(dtdType, element, attribute)) {
                            distinguishing.put(element, attribute);
                        } else {
                            nonDistinguishing.put(element, attribute);
                        }
                    }
                }
            }
        }

        if (isVerbose()) {
            System.out.format("Distinguishing Elements: %s"
                + CldrUtility.LINE_SEPARATOR, distinguishing);
            System.out.format("Nondistinguishing Elements: %s"
                + CldrUtility.LINE_SEPARATOR, nonDistinguishing);
            System.out.format("Skipped %s" + CldrUtility.LINE_SEPARATOR,
                skipAttributes);
        }
    }

    /**
     * The verbose output shows the results of 1..3 \u00a4 signs.
     */
    public void checkCurrency() {
        Map<String, Set<R2<String, Integer>>> results = new TreeMap<>(
            Collator.getInstance(ULocale.ENGLISH));
        for (ULocale locale : ULocale.getAvailableLocales()) {
            if (locale.getCountry().length() != 0) {
                continue;
            }
            for (int i = 1; i < 4; ++i) {
                NumberFormat format = getCurrencyInstance(locale, i);
                for (Currency c : new Currency[] { Currency.getInstance("USD"),
                    Currency.getInstance("EUR"),
                    Currency.getInstance("INR") }) {
                    format.setCurrency(c);
                    final String formatted = format.format(12345.67);
                    Set<R2<String, Integer>> set = results.get(formatted);
                    if (set == null) {
                        results.put(formatted,
                            set = new TreeSet<>());
                    }
                    set.add(Row.of(locale.toString(), Integer.valueOf(i)));
                }
            }
        }
        for (String formatted : results.keySet()) {
            logln(formatted + "\t" + results.get(formatted));
        }
    }

    private static NumberFormat getCurrencyInstance(ULocale locale, int type) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        if (type > 1) {
            DecimalFormat format2 = (DecimalFormat) format;
            String pattern = format2.toPattern();
            String replacement = "\u00a4\u00a4";
            for (int i = 2; i < type; ++i) {
                replacement += "\u00a4";
            }
            pattern = pattern.replace("\u00a4", replacement);
            format2.applyPattern(pattern);
        }
        return format;
    }

    private UnicodeSet safeExemplars(CLDRFile file, String string) {
        final UnicodeSet result = file.getExemplarSet(string,
            WinningChoice.NORMAL);
        return result != null ? result : new UnicodeSet();
    }

    public void TestAPath() {
        // <month type="1">1</month>
        String path = "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/months/monthContext[@type=\"format\"]/monthWidth[@type=\"abbreviated\"]/month[@type=\"1\"]";
        CLDRFile root = testInfo.getRoot();
        logln("path: " + path);
        String fullpath = root.getFullXPath(path);
        logln("fullpath: " + fullpath);
        String value = root.getStringValue(path);
        logln("value: " + value);
        Status status = new Status();
        String source = root.getSourceLocaleID(path, status);
        logln("locale: " + source);
        logln("status: " + status);
    }

    public void TestDefaultContents() {
        Set<String> defaultContents = Inheritance.defaultContents;
        Multimap<String, String> parentToChildren = Inheritance.parentToChildren;

        // Put a list of locales that should be default content here.
        final String expectDC[] = {
            "os_GE" // see CLDR-14118
        };
        for(final String locale : expectDC) {
            assertTrue("expect "+locale+" to be a default content locale", defaultContents.contains(locale));
        }

        if (DEBUG) {
            Inheritance.showChain("", "", "root");
        }

        for (String locale : defaultContents) {
            CLDRFile cldrFile;
            try {
                cldrFile = testInfo.getCLDRFile(locale, false);
            } catch (RuntimeException e) {
                logln("Can't open default content file:\t" + locale);
                continue;
            }
            // we check that the default content locale is always empty
            for (Iterator<String> it = cldrFile.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.contains("/identity")) {
                    continue;
                }
                errln("Default content file not empty:\t" + locale);
                showDifferences(locale);
                break;
            }
        }

        // check that if a locale has any children, that exactly one of them is
        // the default content. Ignore locales with variants

        for (Entry<String, Collection<String>> localeAndKids : parentToChildren.asMap().entrySet()) {
            String locale = localeAndKids.getKey();
            if (locale.equals("root")) {
                continue;
            }

            Collection<String> rawChildren = localeAndKids.getValue();

            // remove variant children
            Set<String> children = new LinkedHashSet<>();
            for (String child : rawChildren) {
                if (new LocaleIDParser().set(child).getVariants().length == 0) {
                    children.add(child);
                }
            }
            if (children.isEmpty()) {
                continue;
            }

            Set<String> defaultContentChildren = new LinkedHashSet<>(children);
            defaultContentChildren.retainAll(defaultContents);
            if (defaultContentChildren.size() == 1) {
                continue;
                // If we're already down to the region level then it's OK not to have
                // default contents.
            } else if (! new LocaleIDParser().set(locale).getRegion().isEmpty()) {
                continue;
            } else if (defaultContentChildren.isEmpty()) {
                Object possible = highestShared(locale, children);
                errln("Locale has children but is missing default contents locale: "
                    + locale + ", children: " + children + "; possible fixes for children:\n" + possible);
            } else {
                errln("Locale has too many defaultContent locales!!: "
                    + locale + ", defaultContents: "
                    + defaultContentChildren);
            }
        }

        // check that each default content locale is likely-subtag equivalent to
        // its parent.

        for (String locale : defaultContents) {
            String maxLocale = LikelySubtags.maximize(locale, likelyData);
            String localeParent = LocaleIDParser.getParent(locale);
            String maxLocaleParent = LikelySubtags.maximize(localeParent,
                likelyData);
            if (locale.equals("ar_001") || locale.equals("nb")) {
                logln("Known exception to likelyMax(locale=" + locale + ")"
                    + " == " + "likelyMax(defaultContent=" + localeParent
                    + ")");
                continue;
            }
            assertEquals("likelyMax(locale=" + locale + ")" + " == "
                + "likelyMax(defaultContent=" + localeParent + ")",
                maxLocaleParent, maxLocale);
        }

    }

    private String highestShared(String parent, Set<String> children) {
        M4<PathHeader, String, String, Boolean> data = ChainedMap.of(new TreeMap<PathHeader, Object>(), new TreeMap<String, Object>(),
            new TreeMap<String, Object>(), Boolean.class);
        CLDRFile parentFile = testInfo.getCLDRFile(parent, true);
        PathHeader.Factory phf = PathHeader.getFactory(testInfo.getEnglish());
        for (String child : children) {
            CLDRFile cldrFile = testInfo.getCLDRFile(child, false);
            for (String path : cldrFile) {
                if (path.contains("/identity")) {
                    continue;
                }
                if (path.contains("provisional") || path.contains("unconfirmed")) {
                    continue;
                }
                String value = cldrFile.getStringValue(path);
                // double-check
                String parentValue = parentFile.getStringValue(path);
                if (value.equals(parentValue)) {
                    continue;
                }
                PathHeader ph = phf.fromPath(path);
                data.put(ph, value, child, Boolean.TRUE);
                data.put(ph, parentValue == null ? "∅∅∅" : parentValue, child, Boolean.TRUE);
            }
        }
        StringBuilder result = new StringBuilder();
        for (Entry<PathHeader, Map<String, Map<String, Boolean>>> entry : data) {
            for (Entry<String, Map<String, Boolean>> item : entry.getValue().entrySet()) {
                result.append("\n")
                .append(entry.getKey())
                .append("\t")
                .append(item.getKey() + "\t" + item.getValue().keySet());
            }
        }
        return result.toString();
    }

    public static class Inheritance {
        public static final Set<String> defaultContents = SUPPLEMENTAL_DATA_INFO
            .getDefaultContentLocales();
        public static final Multimap<String, String> parentToChildren;

        static {
            Multimap<String, String> _parentToChildren = TreeMultimap.create();
            for (String child : testInfo.getCldrFactory().getAvailable()) {
                if (child.equals("root")) {
                    continue;
                }
                String localeParent = LocaleIDParser.getParent(child);
                _parentToChildren.put(localeParent, child);
            }
            parentToChildren = ImmutableMultimap.copyOf(_parentToChildren);
        }

        public static void showChain(String prefix, String gparent, String current) {
            Collection<String> children = parentToChildren.get(current);
            if (children == null) {
                throw new IllegalArgumentException();
            }
            prefix += current + (defaultContents.contains(current) ? "*" : "")
                + (isLikelyEquivalent(gparent, current) ? "~" : "") + "\t";

            // find leaves
            Set<String> parents = new LinkedHashSet<>(children);
            parents.retainAll(parentToChildren.keySet());
            Set<String> leaves = new LinkedHashSet<>(children);
            leaves.removeAll(parentToChildren.keySet());
            if (!leaves.isEmpty()) {
                List<String> presentation = new ArrayList<>();
                boolean gotDc = false;
                for (String s : leaves) {
                    String shown = s;
                    if (isLikelyEquivalent(current, s)) {
                        shown += "~";
                    }
                    if (defaultContents.contains(s)) {
                        gotDc = true;
                        shown += "*";
                    }
                    if (!shown.equals(s)) {
                        presentation.add(0, shown);
                    } else {
                        presentation.add(shown);
                    }
                }
                if (!gotDc) {
                    int debug = 0;
                }
                if (leaves.size() == 1) {
                    System.out.println(prefix + Joiner.on(" ").join(presentation));
                } else {
                    System.out.println(prefix + "{" + Joiner.on(" ").join(presentation) + "}");
                }
            }
            for (String parent : parents) {
                showChain(prefix, current, parent);
            }
        }

        static boolean isLikelyEquivalent(String locale1, String locale2) {
            if (locale1.equals(locale2)) {
                return true;
            }
            try {
                String maxLocale1 = LikelySubtags.maximize(locale1, likelyData);
                String maxLocale2 = LikelySubtags.maximize(locale2, likelyData);
                return maxLocale1 != null && Objects.equal(maxLocale1, maxLocale2);
            } catch (Exception e) {
                return false;
            }
        }
    }

    static final Map<String, String> likelyData = SUPPLEMENTAL_DATA_INFO
        .getLikelySubtags();

    public void TestLikelySubtagsComplete() {
        LanguageTagParser ltp = new LanguageTagParser();
        for (String locale : testInfo.getCldrFactory().getAvailable()) {
            if (locale.equals("root")) {
                continue;
            }
            String maxLocale = LikelySubtags.maximize(locale, likelyData);
            if (maxLocale == null) {
                errln("Locale missing likely subtag: " + locale);
                continue;
            }
            ltp.set(maxLocale);
            if (ltp.getLanguage().isEmpty() || ltp.getScript().isEmpty()
                || ltp.getRegion().isEmpty()) {
                errln("Locale has defective likely subtag: " + locale + " => "
                    + maxLocale);
            }
        }
    }

    private void showDifferences(String locale) {
        CLDRFile cldrFile = testInfo.getCLDRFile(locale, false);
        final String localeParent = LocaleIDParser.getParent(locale);
        CLDRFile parentFile = testInfo.getCLDRFile(localeParent, true);
        int funnyCount = 0;
        for (Iterator<String> it = cldrFile.iterator("",
            cldrFile.getComparator()); it.hasNext();) {
            String path = it.next();
            if (path.contains("/identity")) {
                continue;
            }
            final String fullXPath = cldrFile.getFullXPath(path);
            if (fullXPath.contains("[@draft=\"unconfirmed\"]")
                || fullXPath.contains("[@draft=\"provisional\"]")) {
                funnyCount++;
                continue;
            }
            logln("\tpath:\t" + path);
            logln("\t\t" + locale + " value:\t<"
                + cldrFile.getStringValue(path) + ">");
            final String parentFullPath = parentFile.getFullXPath(path);
            logln("\t\t" + localeParent + " value:\t<"
                + parentFile.getStringValue(path) + ">");
            logln("\t\t" + locale + " fullpath:\t" + fullXPath);
            logln("\t\t" + localeParent + " fullpath:\t" + parentFullPath);
        }
        logln("\tCount of non-approved:\t" + funnyCount);
    }

    enum MissingType {
        plurals, main_exemplars, no_main, collation, index_exemplars, punct_exemplars
    }

    public void TestCoreData() {
        Set<String> availableLanguages = testInfo.getCldrFactory()
            .getAvailableLanguages();
        PluralInfo rootRules = SUPPLEMENTAL_DATA_INFO.getPlurals(
            PluralType.cardinal, "root");
        Multimap<MissingType, Comparable> errors = TreeMultimap.create();
        errors.put(MissingType.collation, "?");

        Multimap<MissingType, Comparable> warnings = TreeMultimap.create();
        warnings.put(MissingType.collation, "?");
        warnings.put(MissingType.index_exemplars, "?");
        warnings.put(MissingType.punct_exemplars, "?");

        Set<String> collations = new HashSet<>();

        // collect collation info
        Factory collationFactory = Factory.make(CLDRPaths.COLLATION_DIRECTORY,
            ".*", DraftStatus.contributed);
        for (String localeID : collationFactory.getAvailable()) {
            if (isTopLevel(localeID)) {
                collations.add(localeID);
            }
        }
        logln(collations.toString());

        Set<String> allLanguages = Builder.with(new TreeSet<String>())
            .addAll(collations).addAll(availableLanguages).freeze();

        for (String localeID : allLanguages) {
            if (localeID.equals("root")) {
                continue; // skip script locales
            }
            if (!isTopLevel(localeID)) {
                continue;
            }
            if (!StandardCodes.isLocaleAtLeastBasic(localeID)) {
                continue;
            }
            errors.clear();
            warnings.clear();

            String name = "Locale:" + localeID + " ("
                + testInfo.getEnglish().getName(localeID) + ")";

            if (!collations.contains(localeID)) {
                warnings.put(MissingType.collation, "missing");
                logln(name + " is missing " + MissingType.collation.toString());
            }

            try {
                CLDRFile cldrFile = testInfo.getCldrFactory().make(localeID,
                    false, DraftStatus.contributed);

                String wholeFileAlias = cldrFile.getStringValue("//ldml/alias");
                if (wholeFileAlias != null) {
                    logln("Whole-file alias:" + name);
                    continue;
                }

                PluralInfo pluralInfo = SUPPLEMENTAL_DATA_INFO.getPlurals(
                    PluralType.cardinal, localeID);
                if (pluralInfo == rootRules) {
                    logln(name + " is missing "
                        + MissingType.plurals.toString());
                    warnings.put(MissingType.plurals, "missing");
                }
                UnicodeSet main = cldrFile.getExemplarSet("",
                    WinningChoice.WINNING);
                if (main == null || main.isEmpty()) {
                    errln("  " + name + " is missing "
                        + MissingType.main_exemplars.toString());
                    errors.put(MissingType.main_exemplars, "missing");
                }
                UnicodeSet index = cldrFile.getExemplarSet("index",
                    WinningChoice.WINNING);
                if (index == null || index.isEmpty()) {
                    logln(name + " is missing "
                        + MissingType.index_exemplars.toString());
                    warnings.put(MissingType.index_exemplars, "missing");
                }
                UnicodeSet punctuation = cldrFile.getExemplarSet("punctuation",
                    WinningChoice.WINNING);
                if (punctuation == null || punctuation.isEmpty()) {
                    logln(name + " is missing "
                        + MissingType.punct_exemplars.toString());
                    warnings.put(MissingType.punct_exemplars, "missing");
                }
            } catch (Exception e) {
                StringWriter x = new StringWriter();
                PrintWriter pw = new PrintWriter(x);
                e.printStackTrace(pw);
                pw.flush();
                errln("  " + name + " is missing main locale data." + x);
                errors.put(MissingType.no_main, x.toString());
            }

            // report errors

            if (errors.isEmpty() && warnings.isEmpty()) {
                logln(name + ": No problems...");
            }
        }
    }

    private boolean isTopLevel(String localeID) {
        return "root".equals(LocaleIDParser.getParent(localeID));
    }

    /**
     * Tests that every dtd item is connected from root
     */
    public void TestDtdCompleteness() {
        for (DtdType type : DtdType.values()) {
            DtdData dtdData = DtdData.getInstance(type);
            Set<Element> descendents = new LinkedHashSet<>();
            dtdData.getDescendents(dtdData.ROOT, descendents);
            Set<Element> elements = dtdData.getElements();
            if (!elements.equals(descendents)) {
                for (Element e : elements) {
                    if (!descendents.contains(e) && !e.equals(dtdData.PCDATA)
                        && !e.equals(dtdData.ANY)) {
                        errln(type + ": Element " + e
                            + " not contained in descendents of ROOT.");
                    }
                }
                for (Element e : descendents) {
                    if (!elements.contains(e)) {
                        errln(type + ": Element " + e
                            + ", descendent of ROOT, not in elements.");
                    }
                }
            }
            LinkedHashSet<Element> all = new LinkedHashSet<>(descendents);
            all.addAll(elements);
            Set<Attribute> attributes = dtdData.getAttributes();
            for (Attribute a : attributes) {
                if (!elements.contains(a.element)) {
                    errln(type + ": Attribute " + a + " isn't for any element.");
                }
            }
        }
    }

    public void TestBasicDTDCompatibility() {

        if (logKnownIssue("cldrbug:11583", "Comment out test until last release data is available for unit tests")) {
            return;
        }

        final String oldCommon = CldrVersion.v22_1.getBaseDirectory() + "/common";

        // set up exceptions
        Set<String> changedToEmpty = new HashSet<>(
            Arrays.asList(new String[] { "version", "languageCoverage",
                "scriptCoverage", "territoryCoverage",
                "currencyCoverage", "timezoneCoverage",
            "skipDefaultLocale" }));
        Set<String> PCDATA = new HashSet<>();
        PCDATA.add("PCDATA");
        Set<String> EMPTY = new HashSet<>();
        EMPTY.add("EMPTY");
        Set<String> VERSION = new HashSet<>();
        VERSION.add("version");

        // test all DTDs
        for (DtdType dtd : DtdType.values()) {
            try {
                ElementAttributeInfo oldDtd = ElementAttributeInfo.getInstance(
                    oldCommon, dtd);
                ElementAttributeInfo newDtd = ElementAttributeInfo
                    .getInstance(dtd);

                if (oldDtd == newDtd) {
                    continue;
                }
                Relation<String, String> oldElement2Children = oldDtd
                    .getElement2Children();
                Relation<String, String> newElement2Children = newDtd
                    .getElement2Children();

                Relation<String, String> oldElement2Attributes = oldDtd
                    .getElement2Attributes();
                Relation<String, String> newElement2Attributes = newDtd
                    .getElement2Attributes();

                for (String element : oldElement2Children.keySet()) {
                    Set<String> oldChildren = oldElement2Children
                        .getAll(element);
                    Set<String> newChildren = newElement2Children
                        .getAll(element);
                    if (newChildren == null) {
                        if (!knownElementExceptions.contains(Pair.of(dtd.toString(), element))) {
                            errln("Old " + dtd + " contains element not in new: <"
                                + element + ">");
                        }
                        continue;
                    }
                    Set<String> funny = containsInOrder(newChildren,
                        oldChildren);
                    if (funny != null) {
                        if (changedToEmpty.contains(element)
                            && oldChildren.equals(PCDATA)
                            && newChildren.equals(EMPTY)) {
                            // ok, skip
                        } else {
                            errln("Old " + dtd + " element <" + element
                                + "> has children Missing/Misordered:\t"
                                + funny + "\n\t\tOld:\t" + oldChildren
                                + "\n\t\tNew:\t" + newChildren);
                        }
                    }

                    Set<String> oldAttributes = oldElement2Attributes
                        .getAll(element);
                    if (oldAttributes == null) {
                        oldAttributes = Collections.emptySet();
                    }
                    Set<String> newAttributes = newElement2Attributes
                        .getAll(element);
                    if (newAttributes == null) {
                        newAttributes = Collections.emptySet();
                    }
                    if (!newAttributes.containsAll(oldAttributes)) {
                        LinkedHashSet<String> missing = new LinkedHashSet<>(
                            oldAttributes);
                        missing.removeAll(newAttributes);
                        if (element.equals(dtd.toString())
                            && missing.equals(VERSION)) {
                            // ok, skip
                        } else {
                            errln("Old " + dtd + " element <" + element
                                + "> has attributes Missing:\t" + missing
                                + "\n\t\tOld:\t" + oldAttributes
                                + "\n\t\tNew:\t" + newAttributes);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                errln("Failure with " + dtd);
            }
        }
    }

    private <T> Set<T> containsInOrder(Set<T> superset, Set<T> subset) {
        if (!superset.containsAll(subset)) {
            LinkedHashSet<T> missing = new LinkedHashSet<>(subset);
            missing.removeAll(superset);
            return missing;
        }
        // ok, we know that they are subsets, try order
        Set<T> result = null;
        DiscreteComparator<T> comp = new DiscreteComparator.Builder<T>(
            Ordering.ARBITRARY).add(superset).get();
        T last = null;
        for (T item : subset) {
            if (last != null) {
                int order = comp.compare(last, item);
                if (order != -1) {
                    if (result == null) {
                        result = new HashSet<>();
                        result.add(last);
                        result.add(item);
                    }
                }
            }
            last = item;
        }
        return result;
    }

    public void TestDtdCompatibility() {

        for (DtdType type : DtdType.values()) {
            DtdData dtdData = DtdData.getInstance(type);
            Map<String, Element> currentElementFromName = dtdData
                .getElementFromName();

            // current has no orphan
            Set<Element> orphans = new LinkedHashSet<>(dtdData
                .getElementFromName().values());
            orphans.remove(dtdData.ROOT);
            orphans.remove(dtdData.PCDATA);
            orphans.remove(dtdData.ANY);
            Set<String> elementsWithoutAlt = new TreeSet<>();
            Set<String> elementsWithoutDraft = new TreeSet<>();
            Set<String> elementsWithoutAlias = new TreeSet<>();
            Set<String> elementsWithoutSpecial = new TreeSet<>();

            for (Element element : dtdData.getElementFromName().values()) {
                Set<Element> children = element.getChildren().keySet();
                orphans.removeAll(children);
                if (type == DtdType.ldml
                    && !SUPPLEMENTAL_DATA_INFO.isDeprecated(type,
                        element.name, "*", "*")) {
                    if (element.getType() == ElementType.PCDATA) {
                        if (element.getAttributeNamed("alt") == null) {
                            elementsWithoutAlt.add(element.name);
                        }
                        if (element.getAttributeNamed("draft") == null) {
                            elementsWithoutDraft.add(element.name);
                        }
                    } else {
                        if (children.size() != 0 && !"alias".equals(element.name)) {
                            if (element.getChildNamed("alias") == null) {
                                elementsWithoutAlias.add(element.name);
                            }
                            if (element.getChildNamed("special") == null) {
                                elementsWithoutSpecial.add(element.name);
                            }
                        }
                    }
                }
            }
            assertEquals(type + " DTD Must not have orphan elements",
                Collections.EMPTY_SET, orphans);
            assertEquals(type
                + " DTD elements with PCDATA must have 'alt' attributes",
                Collections.EMPTY_SET, elementsWithoutAlt);
            assertEquals(type
                + " DTD elements with PCDATA must have 'draft' attributes",
                Collections.EMPTY_SET, elementsWithoutDraft);
            assertEquals(type
                + " DTD elements with children must have 'alias' elements",
                Collections.EMPTY_SET, elementsWithoutAlias);
            assertEquals(
                type
                + " DTD elements with children must have 'special' elements",
                Collections.EMPTY_SET, elementsWithoutSpecial);

            if (logKnownIssue("cldrbug:11583", "Comment out test until last release data is available for unit tests")) {
                return;
            }

            for (CldrVersion version : CldrVersion.CLDR_VERSIONS_DESCENDING) {
                if (version == CldrVersion.unknown || version == CldrVersion.baseline) {
                    continue;
                }
                DtdData dtdDataOld;
                try {
                    dtdDataOld = DtdData.getInstance(type, version.toString());
                } catch (IllegalArgumentException e) {
                    boolean tooOld = false;
                    switch (type) {
                    case ldmlBCP47:
                    case ldmlICU:
                        tooOld = version.isOlderThan(CldrVersion.v1_7_2);
                        break;
                    case keyboard:
                    case platform:
                        tooOld = version.isOlderThan(CldrVersion.v22_1);
                        break;
                    default:
                        break;
                    }
                    if (tooOld) {
                        continue;
                    } else {
                        errln(version + ": " + e.getClass().getSimpleName() + ", " + e.getMessage());
                        continue;
                    }
                }
                // verify that if E is in dtdDataOld, then it is in dtdData, and
                // has at least the same children and attributes
                for (Entry<String, Element> entry : dtdDataOld
                    .getElementFromName().entrySet()) {
                    Element oldElement = entry.getValue();
                    Element newElement = currentElementFromName.get(entry
                        .getKey());
                    if (knownElementExceptions.contains(Pair.of(type.toString(), oldElement.getName()))) {
                        continue;
                    }
                    if (assertNotNull(type
                        + " DTD for trunk must be superset of v" + version
                        + ", and must contain «" + oldElement.getName()
                        + "»", newElement)) {
                        // TODO Check order also
                        for (Element oldChild : oldElement.getChildren()
                            .keySet()) {
                            if (oldChild == null) {
                                continue;
                            }
                            Element newChild = newElement
                                .getChildNamed(oldChild.getName());

                            if (knownChildExceptions.contains(Pair.of(newElement.getName(), oldChild.getName()))) {
                                continue;
                            }
                            assertNotNull(
                                type + " DTD - Trunk children of «"
                                    + newElement.getName()
                                    + "» must be superset of v"
                                    + version + ", and must contain «"
                                    + oldChild.getName() + "»",
                                    newChild);
                        }
                        for (Attribute oldAttribute : oldElement
                            .getAttributes().keySet()) {
                            Attribute newAttribute = newElement
                                .getAttributeNamed(oldAttribute.getName());

                            if (knownAttributeExceptions.contains(Pair.of(newElement.getName(), oldAttribute.getName()))) {
                                continue;
                            }
                            assertNotNull(
                                type + " DTD - Trunk attributes of «"
                                    + newElement.getName()
                                    + "» must be superset of v"
                                    + version + ", and must contain «"
                                    + oldAttribute.getName() + "»",
                                    newAttribute);
                        }
                    }
                }
            }
        }
    }

    /**
     * Compare each path to each other path for every single file in CLDR
     */
    public void TestDtdComparison() {
        // try some simple paths for regression

        sortPaths(
            DtdData.getInstance(DtdType.ldml).getDtdComparator(null),
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/dateTimeFormatLength[@type=\"full\"]/dateTimeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats");

        sortPaths(
            DtdData.getInstance(DtdType.supplementalData).getDtdComparator(
                null),
            "//supplementalData/territoryContainment/group[@type=\"419\"][@contains=\"013 029 005\"][@grouping=\"true\"]",
            "//supplementalData/territoryContainment/group[@type=\"003\"][@contains=\"021 013 029\"][@grouping=\"true\"]");

    }

    public void TestDtdComparisonsAll() {
        if (getInclusion() <= 5) { // Only run this test in exhaustive mode.
            return;
        }
        for (File file : CLDRConfig.getInstance().getAllCLDRFilesEndingWith(".xml")) {
            checkDtdComparatorFor(file, null);
        }
    }

    public void checkDtdComparatorForResource(String fileToRead,
        DtdType overrideDtdType) {
        MyHandler myHandler = new MyHandler(overrideDtdType);
        XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
        try {
            myHandler.fileName = fileToRead;
            xfr.read(myHandler.fileName, TestBasic.class, -1, true);
            logln(myHandler.fileName);
        } catch (Exception e) {
            Throwable t = e;
            StringBuilder b = new StringBuilder();
            String indent = "";
            while (t != null) {
                b.append(indent).append(t.getMessage());
                indent = indent.isEmpty() ? "\n\t\t" : indent + "\t";
                t = t.getCause();
            }
            errln(b.toString());
            return;
        }
        DtdData dtdData = DtdData.getInstance(myHandler.dtdType);
        sortPaths(dtdData.getDtdComparator(null), myHandler.data);
    }

    public void checkDtdComparatorFor(File fileToRead, DtdType overrideDtdType) {
        MyHandler myHandler = new MyHandler(overrideDtdType);
        XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
        try {
            myHandler.fileName = PathUtilities.getNormalizedPathString(fileToRead);
            xfr.read(myHandler.fileName, -1, true);
            logln(myHandler.fileName);
        } catch (Exception e) {
            Throwable t = e;
            StringBuilder b = new StringBuilder();
            String indent = "";
            while (t != null) {
                b.append(indent).append(t.getMessage());
                indent = indent.isEmpty() ? "\n\t\t" : indent + "\t";
                t = t.getCause();
            }
            errln(b.toString());
            return;
        }
        DtdData dtdData = DtdData.getInstance(myHandler.dtdType);
        sortPaths(dtdData.getDtdComparator(null), myHandler.data);
    }

    static class MyHandler extends XMLFileReader.SimpleHandler {
        private String fileName;
        private DtdType dtdType;
        private final Set<String> data = new LinkedHashSet<>();

        public MyHandler(DtdType overrideDtdType) {
            dtdType = overrideDtdType;
        }

        @Override
        public void handlePathValue(String path, @SuppressWarnings("unused") String value) {
            if (dtdType == null) {
                try {
                    dtdType = DtdType.fromPath(path);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Can't read " + fileName, e);
                }
            }
            data.add(path);
        }
    }

    public void sortPaths(Comparator<String> dc, Collection<String> paths) {
        String[] array = paths.toArray(new String[paths.size()]);
        sortPaths(dc, array);
    }

    public void sortPaths(Comparator<String> dc, String... array) {
        Arrays.sort(array, 0, array.length, dc);
    }
    // public void TestNewDtdData() moved to TestDtdData

    public void testZhExemplars() {
        UnicodeSet TGH2003_L1 = new UnicodeSet().addAll("一丁七万丈三上下不与丐丑专且世丘丙业丛东丝丢两严丧个丫中丰串临丸丹为主丽举乃久么义之乌乍乎乏乐乒乓乔乖乘乙九乞也习乡书买乱乳乾了予争事二于亏云互五井亚些亡亢交亥亦产亩享京亭亮亲人亿什仁仅仆仇今介仍从仑仓仔他仗付仙代令以仪们仰仲件价任份仿企伊伍伏伐休众优伙会伞伟传伤伦伪伯估伴伶伸伺似佃但位低住佐佑体何余佛作你佣佩佳使侄侈例侍供依侠侣侥侦侧侨侮侯侵便促俄俊俏俐俗俘保信俩俭修俯俱俺倍倒倔倘候倚借倡倦债值倾假偎偏做停健偶偷偿傅傍储催傲傻像僚僧僵僻儒儿允元兄充兆先光克免兑兔党兜兢入全八公六兰共关兴兵其具典兹养兼兽冀内冈冉册再冒冗写军农冠冤冥冬冯冰冲决况冶冷冻净凄准凉凌减凑凛凝几凡凤凭凯凰凳凶凸凹出击函凿刀刁刃分切刊刑划列刘则刚创初删判刨利别刮到制刷券刹刺刻剂剃削前剑剔剖剥剧剩剪副割剿劈力劝办功加务劣动助努劫励劲劳势勃勇勉勋勒勘募勤勺勾勿匀包匆匈匕化北匙匠匣匪匹区医匾匿十千升午卉半华协卑卒卓单卖南博卜占卡卢卤卦卧卫卯印危即却卵卷卸卿厂厅历厉压厌厕厘厚原厢厦厨去县叁参又叉及友双反发叔取受变叙叛叠口古句另叨叩只叫召叭叮可台史右叶号司叹叼叽吁吃各吆合吉吊同名后吏吐向吓吕吗君吝吞吟吠否吧吨吩含听吭吮启吱吴吵吸吹吻吼吾呀呆呈告呐呕员呛呜呢周味呵呻呼命咄咋和咏咐咒咕咖咙咧咨咪咬咱咳咸咽哀品哄哆哇哈哉响哎哑哗哟哥哦哨哩哪哭哮哲哺哼唁唆唇唉唐唠唤唧唬售唯唱唾啃啄商啊啡啤啥啦啪啰啸啼喂善喇喉喊喘喜喝喧喳喷喻嗅嗓嗜嗡嗦嗽嘀嘉嘛嘱嘲嘴嘶嘹嘻嘿器噩噪嚎嚣嚷嚼囊囚四回因团囤园困囱围固国图圃圆圈土圣在地场圾址均坊坎坏坐坑块坚坛坝坟坠坡坤坦坪坯坷垂垃垄型垒垛垢垦垫垮埂埃埋城域埠培基堂堆堕堡堤堪堰堵塌塑塔塘塞填境墅墓墙增墟墨墩壁壤士壬壮声壳壶壹处备复夏夕外多夜够大天太夫夭央夯失头夷夸夹夺奇奈奉奋奏契奔奖套奠奢奥女奴奶奸她好如妃妄妆妇妈妒妓妖妙妥妨妮妹妻姆姊始姐姑姓委姚姜姥姨姻姿威娃娄娇娘娜娟娥娱娶婆婉婚婪婴婶婿媒媚媳嫁嫂嫉嫌嫩子孔孕字存孙孝孟季孤学孩孵孽宁它宅宇守安宋完宏宗官宙定宛宜宝实宠审客宣室宦宪宫宰害宴宵家容宽宾宿寂寄寅密寇富寒寓寝寞察寡寥寨寸对寺寻导寿封射将尉尊小少尔尖尘尚尝尤尧尬就尴尸尺尼尽尾尿局屁层居屈屉届屋屎屏屑展属屠屡履屯山屹屿岁岂岔岖岗岛岩岭岳岸峡峦峨峭峰峻崇崎崔崖崛崩崭嵌巅巍川州巡巢工左巧巨巩巫差己已巳巴巷巾币市布帅帆师希帐帕帖帘帚帜帝带席帮帷常帽幅幌幕幢干平年并幸幻幼幽广庄庆庇床序庐库应底店庙庚府庞废度座庭庵庶康庸廉廊廓延廷建开异弃弄弊式弓引弗弘弛弟张弥弦弧弯弱弹强归当录形彤彩彪彬彭彰影役彻彼往征径待很徊律徐徒得徘徙御循微德徽心必忆忌忍志忘忙忠忧快忱念忽忿怀态怎怒怔怕怖怜思怠怡急性怨怪怯总恃恋恍恐恒恕恢恤恨恩恬恭息恰恳恶恼悄悉悍悔悖悟悠患悦您悬悯悲悴悼情惊惋惑惕惜惟惠惦惧惨惩惫惭惯惰想惶惹愁愈愉意愕愚感愣愤愧愿慈慌慎慕慢慧慨慰慷憋憎憔憨憾懂懈懊懒懦戈戊戌戎戏成我戒或战戚截戳戴户房所扁扇手才扎扑扒打扔托扛扣执扩扫扬扭扮扯扰扳扶批扼找承技抄把抑抒抓投抖抗折抚抛抠抡抢护报披抬抱抵抹押抽拂拄担拆拇拉拌拍拎拐拒拓拔拖拗拘拙招拜拟拢拣拥拦拧拨择括拭拯拱拳拴拷拼拽拾拿持挂指按挎挑挖挚挟挠挡挣挤挥挨挪挫振挺挽捂捅捆捉捌捍捎捏捐捕捞损捡换捣捧据捶捷捺捻掀掂授掉掌掏掐排掘掠探接控推掩措掰掷掺揉揍描提插握揣揩揪揭援揽搀搁搂搅搏搓搔搜搞搬搭携摄摆摇摊摔摘摧摩摸摹撇撑撒撕撞撤撩撬播撮撰撵撼擂擅操擎擒擦攀支收改攻放政故效敌敏救教敛敞敢散敦敬数敲整敷文斋斌斑斗料斜斟斤斥斧斩断斯新方施旁旅旋族旗无既日旦旧旨早旬旭旱时旷旺昂昆昌明昏易昔星映春昧昨昭是昼显晃晋晌晒晓晕晚晦晨普景晰晴晶智晾暂暇暑暖暗暮暴曙曝曰曲更曹曼曾替最月有朋服朗望朝期朦木未末本术朱朴朵机朽杀杂权杆杉李杏材村杖杜束杠条来杨杭杯杰松板极构枉析枕林枚果枝枢枣枪枫枯架柄柏某柑柒染柔柜柠查柬柱柳柴柿栅标栈栋栏树栓栖栗校株样核根格栽桂桃框案桌桐桑桔档桥桦桨桩桶梁梅梆梗梢梦梧梨梭梯械梳检棉棋棍棒棕棘棚棠森棱棵棺椅植椎椒椭椰椿楚楷楼概榄榆榔榕榜榨榴槐槛槽樟模横樱橄橘橙橡橱檀檐檬欠次欢欣欧欲欺款歇歉歌止正此步武歧歪歹死歼殃殉殊残殖殴段殷殿毁毅母每毒比毕毙毛毡毫毯氏民氓气氛氢氧氨氮氯水永汁求汇汉汗汛汝汞江池污汤汪汰汹汽沁沃沈沉沐沙沛沟没沥沦沧沪沫沮河沸油治沼沽沾沿泄泉泊泌法泛泞泡波泣泥注泪泰泳泵泻泼泽洁洋洒洗洛洞津洪洲活洼洽派流浅浆浇浊测济浏浑浓浙浦浩浪浮浴海浸涂消涉涌涕涛涝涡涣涤润涧涨涩涮涯液涵淀淆淋淌淑淘淡淤淫淮深淳混淹添清渊渐渔渗渝渠渡渣渤温港渲渴游渺湃湖湘湾湿溃溅溉源溜溢溪溯溶溺滇滋滑滔滚滞满滤滥滨滩滴漂漆漏漓演漠漫漱漾潇潘潜潭潮澄澈澎澜澡澳激濒瀑灌火灭灯灰灵灶灸灼灾灿炉炊炎炒炕炫炬炭炮炸点炼烁烂烈烘烙烛烟烤烦烧烫热烹焉焊焕焚焦焰然煌煎煞煤照煮煽熄熊熏熔熙熟熬燃燕燥爆爪爬爱爵父爷爸爹爽片版牌牙牛牡牢牧物牲牵特牺犀犁犬犯状犹狂狈狐狗狞狠狡独狭狮狰狱狸狼猎猖猛猜猩猪猫猬献猴猾猿玄率玉王玖玛玩玫环现玲玷玻珊珍珠班球琅理琉琐琢琳琴琼瑙瑞瑟瑰璃璧瓜瓢瓣瓤瓦瓶瓷甘甚甜生甥用甩甫田由甲申电男甸画畅界畏畔留畜略番畴畸疆疏疑疗疙疚疟疤疫疮疯疲疹疼疾病症痊痒痕痘痛痢痪痰痴痹瘟瘤瘦瘩瘪瘫瘸瘾癌癣癸登白百皂的皆皇皓皖皮皱皿盆盈益盏盐监盒盔盖盗盘盛盟目盯盲直相盹盼盾省眉看真眠眨眯眶眷眼着睁睐睛睡督睦睫睬睹瞄瞅瞎瞒瞧瞩瞪瞬瞭瞳瞻矗矛矢矣知矩矫短矮石矾矿码砂砌砍研砖砚砰破砸砾础硅硕硝硫硬确碌碍碎碑碗碘碟碧碰碱碳碾磁磅磊磕磨磷礁示礼社祀祈祖祝神祟祠祥票祭祷祸禀禁禄禅福禹离禽禾秀私秃秆秉秋种科秒秘租秤秦秧秩积称秸移秽稀程稍税稚稠稳稻稼稽稿穆穗穴究穷空穿突窃窄窍窑窒窖窗窘窜窝窟窥窿立竖站竞竟章竣童竭端竹竿笋笑笔笙笛符笨第笼等筋筏筐筑筒答策筛筝筷筹签简箕算管箩箫箭箱篇篓篡篮篱篷簇簧簸簿籍米类籽粉粒粗粘粟粤粥粪粮粱粹精糊糕糖糙糟糠糯系紊素索紧紫累絮繁纠红纤约级纪纫纬纯纱纲纳纵纷纸纹纺纽线练组绅细织终绊绍绎经绑绒结绕绘给绚络绝绞统绢绣继绩绪续绰绳维绵绷绸综绽绿缀缅缆缉缎缓缔缕编缘缚缝缠缤缩缭缰缴缸缺罐网罕罗罚罢罩罪置署羊美羔羚羞羡群羹羽翁翅翔翘翠翩翰翻翼耀老考者而耍耐耕耗耘耙耳耸耻耽耿聂聆聊聋职联聘聚聪肃肆肇肉肋肌肖肘肚肛肝肠股肢肤肥肩肪肮肯育肴肺肾肿胀胁胃胆背胎胖胚胜胞胡胧胰胳胶胸能脂脆脉脊脏脐脑脓脖脚脯脱脸脾腊腋腐腔腕腥腮腰腹腺腻腾腿膀膊膏膛膜膝膨臀臂臊臣自臭至致臼舀舅舆舌舍舒舔舞舟航般舰舱舵舶船艇艘良艰色艳艺艾节芋芒芙芜芝芥芦芬芭芯花芳芹芽苇苍苏苑苔苗苛苞苟若苦英苹茁茂范茄茅茉茎茧茫茬茵茶茸荆草荐荒荔荡荣荤荧荫药荷莉莫莱莲获莹莺莽菇菊菌菜菠菩菱菲萄萌萍萎萝萤营萧萨落著葛葡董葫葬葱葵蒂蒋蒙蒜蒲蒸蓄蓉蓝蓬蔑蔓蔗蔚蔡蔬蔼蔽蕉蕊蕴蕾薄薇薛薪薯藉藏藐藕藤藻蘑虎虏虐虑虚虫虹虽虾蚀蚁蚂蚊蚌蚓蚕蚣蚤蚪蚯蛀蛇蛋蛙蛛蛤蛮蛾蜀蜂蜈蜒蜓蜕蜗蜘蜜蜡蜻蝇蝉蝌蝎蝗蝙蝠蝴蝶螃融螺蟀蟆蟋蟹蠕蠢血衅行衍衔街衙衡衣补表衫衬衰衷袁袄袋袍袖袜被袭袱裁裂装裕裙裤裳裸裹褂褐褒褥褪襟西要覆见观规觅视览觉角解触言誉誊誓警譬计订认讥讨让训议讯记讲讳讶许讹论讼讽设访诀证评识诈诉诊词译试诗诚话诞诡询该详诫诬语误诱诲说诵请诸诺读诽课谁调谅谆谈谊谋谍谎谐谓谚谜谢谣谤谦谨谬谭谱谴谷豁豆豌豚象豪豫豹豺貌贝贞负贡财责贤败账货质贩贪贫贬购贮贯贰贱贴贵贷贸费贺贻贼贾贿赁赂赃资赋赌赎赏赐赔赖赘赚赛赞赠赡赢赣赤赦赫走赴赵赶起趁超越趋趟趣足趴趾跃跋跌跑跛距跟跤跨跪路跳践跷跺踊踏踢踩踪踱蹂蹄蹈蹋蹦蹬蹭蹲躁躏身躬躯躲躺车轧轨轩转轮软轰轴轻载轿较辅辆辈辉辐辑输辖辗辙辛辜辞辟辣辨辩辫辰辱边辽达迁迂迄迅过迈迎运近返还这进远违连迟迢迪迫迭述迷迹追退送适逃逆选逊透逐递途逗通逛逝逞速造逢逮逸逻逼逾遂遇遍遏道遗遣遥遭遮遵避邀邑邓那邦邪邮邻郁郊郎郑部郭都鄂鄙酉酌配酒酗酝酣酥酪酬酱酵酷酸酿醇醉醋醒采释里重野量金鉴针钉钓钙钝钞钟钠钢钥钦钧钩钮钱钳钻钾铁铃铅铐铛铜铝铭铲银铸铺链销锁锄锅锈锋锌锐错锚锡锣锤锥锦键锯锰锹锻镀镇镐镑镜镰镶长门闪闭问闯闰闲间闷闸闹闺闻闽阀阁阅阎阐阔队防阳阴阵阶阻阿附际陆陈陋陌降限陕陡院除陨险陪陵陶陷隅隆隋随隐隔隘隙障隧隶难雀雁雄雅集雇雌雏雕雨雪雳零雷雹雾需霄震霉霍霎霜霞露霸霹青靖静非靠靡面革靴靶鞋鞍鞠鞭韧韩韭音韵页顶顷项顺须顽顾顿颁颂预颅领颇颈颊频颓颖颗题颜额颠颤风飘飞食餐饥饭饮饰饱饲饵饶饺饼饿馁馅馆馈馋馍馏馒首香馨马驮驯驰驱驳驴驶驹驻驼驾骂骄骆骇验骏骑骗骚骡骤骨髓高鬓鬼魁魂魄魅魏魔鱼鲁鲍鲜鲤鲨鲫鲸鳄鳍鳖鳞鸟鸡鸣鸥鸦鸭鸯鸳鸵鸽鸿鹃鹅鹉鹊鹏鹤鹦鹰鹿麦麻黄黎黑黔默黯鼎鼓鼠鼻齐齿龄龙龟");
        CLDRFile zh = CLDRConfig.getInstance().getCldrFactory().make("zh", true);

        UnicodeSet main = zh.getExemplarSet(ExemplarType.main, WinningChoice.WINNING);

        UnicodeSet aux = zh.getExemplarSet(ExemplarType.auxiliary, WinningChoice.WINNING);

        UnicodeSet missing = new UnicodeSet(TGH2003_L1).removeAll(main).removeAll(aux);
        if (!assertTrue("TGH2003_L1 ⊉ main+aux", missing.isEmpty())) {
            System.out.println("main: " + main.size() + "\n" + main.toPattern(false));
            System.out.println("aux: " + aux.size() + "\n" + aux.toPattern(false));
            System.out.println("in TGH2003_L1 but not main+aux: " + missing.size() + "\n" + missing.toPattern(false));
            UnicodeSet extra = new UnicodeSet(main).addAll(aux).removeAll(TGH2003_L1);
            System.out.println("in main+aux but not TGH2003_L1: " + extra.size() + "\n" + extra.toPattern(false));
        }
    }

}
