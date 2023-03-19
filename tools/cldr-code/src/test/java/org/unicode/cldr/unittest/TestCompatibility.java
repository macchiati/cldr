package org.unicode.cldr.unittest;

import static org.unicode.cldr.util.PathUtilities.getNormalizedPathString;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.StandardCodes.LstrType;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Validity;
import org.unicode.cldr.util.Validity.Status;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;

import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.util.VersionInfo;

public class TestCompatibility extends TestFmwkPlus {
    private static final File ARCHIVE = new File(CLDRPaths.ARCHIVE_DIRECTORY);

    public static void main(String[] args) {
        new TestCompatibility().run(args);
    }

    public void testBCP47() {
        testBCP47(ARCHIVE);

    }

    public void testCurrencyValidityVsBCP47() {
        SupplementalDataInfo sdi = CLDRConfig.getInstance().getSupplementalDataInfo();
        final Set<String> lowerCurrencies = sdi.bcp47Key2Subtypes.get("cu");
        Set<String> bcp47Currencies = lowerCurrencies.stream().map(x -> x.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());

        Map<String, Status> validityCurrencies = Validity.getInstance().getCodeToStatus(LstrType.currency);

        assertEquals("validity contains bcp47", Collections.EMPTY_SET, Sets.difference(validityCurrencies.keySet(), bcp47Currencies));
        assertEquals("bcp47 contains validity", Collections.EMPTY_SET, Sets.difference(bcp47Currencies, validityCurrencies.keySet()));
    }

    private void testBCP47(File dir) {
        Map<Pair<String,String>, Bcp47Data> bcp47DataMap = new TreeMap<>();
        // gather data
        for (File file : dir.listFiles()) {
            String name = file.getName();
            if (name.startsWith(".") || name.endsWith(".md")) {
                continue;
            }
            String version = name.split("-")[1];
            testBCP47Files(bcp47DataMap, version, new File(file, "common/bcp47"));
        }
        for (Entry<Pair<String, String>, Bcp47Data> entry : bcp47DataMap.entrySet()) {
            final Pair<String, String> key = entry.getKey();
            final Bcp47Data value = entry.getValue();

            NavigableSet<VersionInfo> sinceValues = value.sinceToVersions.keySet();
            NavigableSet<VersionInfo> versions = value.sinceToVersions.get(sinceValues.last());

            assertEquals(key.getFirst() + "\t" + key.getSecond() + "\tfirst version appeared = since value", versions.first().getVersionString(2, 2), sinceValues.last().getVersionString(2, 2));

            logln(
                key.getFirst()
                + "\t" + key.getSecond()
                + "\t" + sinceValues.first()
                + "\t" + versions.first()
                );
        }
    }

    class Bcp47Data {
        TreeMultimap<VersionInfo, VersionInfo> sinceToVersions = TreeMultimap.create();

        public void put(String version, String keySince) {
            if (keySince == null) {
                keySince = "1.7.2";
            }
            final VersionInfo since = clean(keySince);
            final VersionInfo cldrVersion = clean(version);
            sinceToVersions.put(since, cldrVersion);
        }
        public VersionInfo clean(String keySince) {
            VersionInfo temp = VersionInfo.getInstance(keySince);
            if (temp.getMajor() < 21) {
                return VersionInfo.getInstance(temp.getMajor(), temp.getMinor());
            } else {
                return VersionInfo.getInstance(temp.getMajor());
            }
        }
        @Override
        public String toString() {
            return sinceToVersions.toString();
        }
    }

    private void testBCP47Files(Map<Pair<String, String>, Bcp47Data> bcp47DataMap, String version, File dir) {
        if (!dir.exists()) {
            return;
        }

        List<Pair<String, String>> data = new ArrayList<>();
        for (File file : dir.listFiles()) {
            for (Pair<String, String> pathValue : XMLFileReader.loadPathValues(file.getAbsolutePath(), data, true)) {
                //         <key name="ca" description="Calendar algorithm key" valueType="incremental" alias="calendar">
                // <type name="buddhist" description="Thai Buddhist calendar"/>
                XPathParts parts = XPathParts.getFrozenInstance(pathValue.getFirst());
                if (!parts.getElement(-1).equals("type")) {
                    continue;
                }
                String keyName = parts.getAttributeValue(-2, "name");
                String keySince = parts.getAttributeValue(-2, "since");
                String typeName = parts.getAttributeValue(-1, "name");
                String typeSince = parts.getAttributeValue(-1, "since");
                add(version, keyName, "", keySince, bcp47DataMap);
                add(version, keyName, typeName, typeSince, bcp47DataMap);
            }
        }
    }

    public void add(String version, String keyName, String typeName, String since, Map<Pair<String, String>, Bcp47Data> bcp47DataMap) {
        Pair<String, String> keyType = Pair.of(keyName, typeName);
        Bcp47Data info = bcp47DataMap.get(keyType);
        if (info == null) {
            bcp47DataMap.put(keyType, info = new Bcp47Data());
        }
        info.put(version, since);
    }

    public void TestReadWrite() throws IOException {
        checkFiles(ARCHIVE);
    }

    private void checkFiles(File dir) throws IOException {
        for (File file : dir.listFiles()) {
            // for now, only look at common
            if (file.getName().equals("main")) {
                checkXmlFile(file);
            } else if (file.isDirectory()) {
                checkFiles(file);
            }
        }
    }

    // for now, only look at common main
    private void checkXmlFile(File file) throws IOException {
        if (!getNormalizedPathString(file).contains("cldr-27.0")) {
            return;
        }
        Factory factory = Factory.make(getNormalizedPathString(file), ".*");
        for (String language : factory.getAvailableLanguages()) {
            CLDRFile cldrFile;
            try {
                cldrFile = factory.make(language, false);
            } catch (Exception e) {
                errln("Couldn't read " + language + ":\t" + e.getLocalizedMessage() + ", in " + getNormalizedPathString(file));
                continue;
            }
            try (StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);) {
                cldrFile.write(pw);
            } catch (Exception e) {
                errln("Couldn't write " + language + ":\t" + e.getLocalizedMessage() + ", in " + getNormalizedPathString(file));
            }
        }
    }
}
