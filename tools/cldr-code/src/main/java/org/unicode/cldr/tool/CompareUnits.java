package org.unicode.cldr.tool;

import java.util.Objects;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;

/**
 * Tool to compare unit durations with -person
 * TODO add output filename
 * @author markdavis
 *
 */
public class CompareUnits {

    public static void main(String[] args) {

        Factory factory = CLDRConfig.getInstance().getMainAndAnnotationsFactory();
        for (String locale : factory.getAvailableLanguages()) {
            System.out.println("# " + locale);
            CLDRFile cldrFile = factory.make(locale, true);
            for (String path : cldrFile.fullIterable()) {
              //ldml/units/unitLength[@type="long"]/unit[@type="duration-day-person"]/gender
                if (path.endsWith("/alias")) {
                    continue;
                }
                if (path.startsWith("//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"duration-") && path.contains("-person")) {
                    String value = cldrFile.getStringValue(path);
                    if (value == null) {
                        continue;
                    }
                   String newPath = path.replace("-person","");
                    String newValue = cldrFile.getStringValue(newPath);
                    if (!Objects.equals(value, newValue)) {
                        System.out.println(locale + "\t" + path + "\t" + value + "\t" + newPath + "\t" + newValue);
                    }
                }
            }
        }
    }
}
