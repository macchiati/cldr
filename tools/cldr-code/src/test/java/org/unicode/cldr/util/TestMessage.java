package org.unicode.cldr.util;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.ibm.icu.message2.FormattedPlaceholder;
import com.ibm.icu.message2.Formatter;
import com.ibm.icu.message2.FormatterFactory;
import com.ibm.icu.message2.MessageFormatter;
import com.ibm.icu.message2.Mf2FunctionRegistry;
import com.ibm.icu.message2.PlainStringFormattedValue;
import com.ibm.icu.text.PersonName;
import com.ibm.icu.text.PersonName.NameField;
import com.ibm.icu.text.PersonNameFormatter;
import com.ibm.icu.text.PersonNameFormatter.Builder;
import com.ibm.icu.text.PersonNameFormatter.Formality;
import com.ibm.icu.text.PersonNameFormatter.Length;
import com.ibm.icu.text.PersonNameFormatter.Options;
import com.ibm.icu.text.PersonNameFormatter.Usage;
import com.ibm.icu.text.SimplePersonName;
import com.ibm.icu.util.ULocale;

public class TestMessage {

    // Each MF2 class and each PersonName class (internal and external) should have reasonable toString methods;
    // otherwise it is hard to debug.


    // This is so that we can pass a set of options, using the Literal construct.
    // https://github.com/unicode-org/message-format-wg/blob/main/spec/syntax.md#expressions
    // Examples:
    // {Hello, {$userObj :person length=LONG usage=ADDRESSING formality=FORMAL options=()}!}
    // {Hello, {$userObj :person length=LONG usage=ADDRESSING formality=FORMAL options=(SORTING,SURNAME_ALLCAPS)}!}


    static final Splitter COMMA_SPLITTER = Splitter.on(",").trimResults();
    static final Joiner COMMA_JOINER = Joiner.on(',');

    @SuppressWarnings("deprecation")
    static final class PersonNameFormatterFactory implements FormatterFactory {

        // Formatter should allow for specification?, eg Formatter<PersonName>
        static class PersonNameFormatterShim implements Formatter {

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

                return commaString.isBlank()
                    ? Collections.emptySet()
                        : Streams.stream(COMMA_SPLITTER.split(commaString))
                        .map(Options::valueOf)
                        .collect(Collectors.toSet());
            }

            // PersonName.Options should be Option (enums are singular)

            @Override
            public String formatToString(Object toFormat, Map<String, Object> variableOptions) {
                return personNameFormatter.formatToString((PersonName) toFormat);
            }

            @Override
            public FormattedPlaceholder format(Object toFormat, Map<String, Object> variableOptions) {
                return new FormattedPlaceholder(toFormat,
                    new PlainStringFormattedValue(formatToString(toFormat, variableOptions)));
            }
        }
        @Override
        public Formatter createFormatter(Locale locale, Map<String, Object> fixedOptions) {
            return new PersonNameFormatterShim(locale, fixedOptions);
        }

    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        final Locale SPANISH = Locale.forLanguageTag("es");

        Mf2FunctionRegistry functionRegistry = Mf2FunctionRegistry.builder()

            // The following doesn't seem to work.
            // .setDefaultFormatterNameForType(PersonNameFormatter.class, "person")
            // For it to work, I suspect simplest would be that the class should be required to implement an interface
            // Should the String be in the same position in both method?

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

        // Check different options.

        List<Set<Options>> optionList = ImmutableList.of(
            ImmutableSet.of(),
            ImmutableSet.of(Options.SORTING),
            ImmutableSet.of(Options.SORTING, Options.SURNAME_ALLCAPS));

        // PersonName.toString should be useful; resulting in locale + ", " + map.toString()

        for (String localeString : Arrays.asList("en", "es")) {
            Locale locale = Locale.forLanguageTag(localeString);


            for (Set<Options> options : optionList ) {
                boolean mfShown = false;
                for (Length length : Length.values()) {
                    for (Formality formality : Formality.values()) {
                        for (Usage usage : Usage.values()) {
                            String pattern = basePattern
                                .replace("%%L", length.toString())
                                .replace("%%F", formality.toString())
                                .replace("%%U", usage.toString())
                                .replace("%%O", "(" + COMMA_JOINER.join(options) + ")")
                                ;

                            // We should be able to set up everything but the locale,
                            // and pass in the locale later.
                            // That way the pattern doesn't need to be parsed each time.

                            MessageFormatter mf = MessageFormatter.builder()
                                .setPattern(pattern)
                                .setFunctionRegistry(functionRegistry)
                                .setLocale(locale)
                                .build();

                            if (mfShown == false) {
                                System.out.println(ULocale.getDisplayLanguage(localeString,
                                    ULocale.ENGLISH) + ": " + mf.getPattern() + "\n");
                                mfShown = true;
                            }

                            String formatted = mf.formatToString(ImmutableMap.of("userObj", personName));
                            Set<Object> arguments = ImmutableSet.of(options, length, formality, usage);

                            System.out.println(locale + "\t" + arguments + "\t➡︎\t" + formatted);
                        }
                        System.out.println();
                    }
                }
            }
        }
        // Question for messages:
        // If an option is not supported should it silently fail?
        //   pros: gives some forward compatibility; an old message will not fail with new option
        //   cons: hides real errors
    }

    // FYI I couldn't build what I wanted to with PersonNames, because we don't have formatted values for them.
}