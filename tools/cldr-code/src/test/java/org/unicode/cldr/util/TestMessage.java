package org.unicode.cldr.util;
import java.text.AttributedCharacterIterator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.ibm.icu.message2.FormattedPlaceholder;
import com.ibm.icu.message2.Formatter;
import com.ibm.icu.message2.FormatterFactory;
import com.ibm.icu.message2.MessageFormatter;
import com.ibm.icu.message2.Mf2FunctionRegistry;
import com.ibm.icu.message2.PlainStringFormattedValue;
import com.ibm.icu.text.ConstrainedFieldPosition;
import com.ibm.icu.text.FormattedValue;
import com.ibm.icu.text.PersonName;
import com.ibm.icu.text.PersonName.NameField;
import com.ibm.icu.text.PersonNameFormatter;
import com.ibm.icu.text.PersonNameFormatter.Builder;
import com.ibm.icu.text.PersonNameFormatter.Formality;
import com.ibm.icu.text.PersonNameFormatter.Length;
import com.ibm.icu.text.PersonNameFormatter.Options;
import com.ibm.icu.text.PersonNameFormatter.Usage;
import com.ibm.icu.text.SimplePersonName;

public class TestMessage {
    // each class should have reasonable toString methods
    @SuppressWarnings("deprecation")

    static final class PersonNameFormatterFactory implements FormatterFactory {

        // Formatter should allow for specification, eg Formatter<PersonName>
        static class PersonNameFormatterShim implements Formatter {
            static final Splitter LIST_SPLITTER = Splitter.on('Ξ').trimResults();
            private final PersonNameFormatter personNameFormatter;

            public PersonNameFormatterShim(Locale locale, Map<String, Object> fixedOptions) {
                Builder builder = PersonNameFormatter.builder();
                for (Entry<String, Object> entry : fixedOptions.entrySet()) {
                    String value = entry.getValue().toString().toUpperCase(Locale.ROOT);
                    switch(entry.getKey()) {
                    case "length": builder.setLength(Length.valueOf(value)); break;
                    case "formality": builder.setFormality(Formality.valueOf(value)); break;
                    case "usage": builder.setUsage(Usage.valueOf(value)); break;
                    case "options": builder.setOptions(fromCommaString(value)); break;
                    default: throw new IllegalArgumentException("Illegal options " + entry);
                    }
                }
                personNameFormatter = builder.setLocale(locale).build();
            }

            Set<Options> fromCommaString(String commaString) {
                return Streams.stream(
                    LIST_SPLITTER.splitToList(commaString))
                    .filter(x -> !"NONE".equals(x))
                    .map(Options::valueOf)
                    .collect(Collectors.toSet());
            }
            // Options should be Option (enums are singular)

            @Override
            public String formatToString(Object toFormat, Map<String, Object> variableOptions) {
                return personNameFormatter.formatToString((PersonName) toFormat);
            }

            @Override
            public FormattedPlaceholder format(Object toFormat, Map<String, Object> variableOptions) {
                return new FormattedPlaceholder(toFormat,
                    new PlainStringFormattedValue(formatToString(toFormat, variableOptions)));
            }

            static class FormattedPersonName implements FormattedValue {

                @Override
                public int length() {
                    // TODO Auto-generated method stub
                    return 0;
                }

                @Override
                public char charAt(int index) {
                    // TODO Auto-generated method stub
                    return 0;
                }

                @Override
                public CharSequence subSequence(int start, int end) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public <A extends Appendable> A appendTo(A appendable) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public boolean nextPosition(ConstrainedFieldPosition cfpos) {
                    // TODO Auto-generated method stub
                    return false;
                }

                @Override
                public AttributedCharacterIterator toCharacterIterator() {
                    // TODO Auto-generated method stub
                    return null;
                }

            }

        }
        @Override
        public Formatter createFormatter(Locale locale, Map<String, Object> fixedOptions) {
            // TODO Auto-generated method stub
            return new PersonNameFormatterShim(locale, fixedOptions);
        }

    }

    public static void main(String[] args) {
        final Locale SPANISH = Locale.forLanguageTag("es");

        Mf2FunctionRegistry functionRegistry = Mf2FunctionRegistry.builder()
            //.setDefaultFormatterNameForType(PersonNameFormatter.class, "person")
            // class should be required to implement an interface
            // items in wrong order??
            .setFormatter("person", new PersonNameFormatterFactory())
            .build();

        final String basePattern = "{Hello, {$userObj :person length=%%L usage=%%U formality=%%F options=%%O}!}";

        PersonName personName = SimplePersonName.builder()
            .addField(NameField.GIVEN, null, "Daniel")
            .addField(NameField.GIVEN2, null, "César Martín")
            .addField(NameField.SURNAME, null, "Brühl")
            .addField(NameField.SURNAME2, null, "González Domingo")
            .setLocale(SPANISH)
            .build();
        // toString of this should be useful; locale + map.toString()

        boolean mfShown = false;
        for (String localeString : Arrays.asList("en", "es")) {
            Locale locale = Locale.forLanguageTag(localeString);

            // check different options. this wouldn't happen in practice
            for (String options : Arrays.asList("none", "sorting", "sortingΞSURNAME_ALLCAPS")) {
                for (Length length : Length.values()) {
                    for (Formality formality : Formality.values()) {
                        for (Usage usage : Usage.values()) {
                            String pattern = basePattern
                                .replace("%%L", length.toString())
                                .replace("%%F", formality.toString())
                                .replace("%%U", usage.toString())
                                .replace("%%O", options)
                                ;

                            MessageFormatter mf = MessageFormatter.builder()
                                .setPattern(pattern)
                                .setFunctionRegistry(functionRegistry)
                                .setLocale(locale)
                                .build();
                            // should be able to set up everything but the locale, and pass in the locale later
                            // that way the pattern doesn't need to be parsed each time.

                            if (mfShown == false) {
                                System.out.println(mf.getPattern() + ", " + mf.getLocale() + "\n");
                                mfShown = true;
                            }

                            String formatted = mf.formatToString(ImmutableMap.of("userObj", personName));
                            Set<Object> arguments = ImmutableSet.of(options, length, formality, usage);

                            System.out.println(locale + "\t" + arguments + "\t☞\t" + formatted);
                        }
                        System.out.println();
                    }
                }
            }
        }
        // Question for messages. if an option is not supported should it silently fail?
        // pros: gives some forward compatibility; an old message will not fail with new option
        // cons: hides real errors
    }
}