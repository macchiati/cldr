package com.ibm.icu.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.unicode.cldr.util.Rational;

public class MeasureR {
    public static final Splitter AND_SPLITTER = Splitter.on("-and-");

    public final Rational amount;
    public final String unit;

    private MeasureR(Number amount, String unit) {
        this.amount = Rational.of(amount);
        this.unit = unit;
    }

    @Override
    public String toString() {
        return amount.toBigDecimal(MathContext.DECIMAL64) + " " + unit;
    }

    @Override
    public boolean equals(Object obj) {
        MeasureR that = (MeasureR) obj;
        return amount.equals(that.amount) && unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, unit);
    }

    public static MeasureR from(Number number, String core_unit) {
        return new MeasureR(number, core_unit);
    }

    public static class MixedMeasure {
        public final List<MeasureR> measures;

        private MixedMeasure(List<MeasureR> measure2List) {
            measures = List.copyOf(measure2List);
        }

        public static final MixedMeasure from(String mixedUnitString, Rational... values) {
            return from(mixedUnitString, Arrays.asList(values));
        }

        public static final MixedMeasure from(String mixedUnitString, List<Rational> values) {
            List<MeasureR> _measures = new ArrayList<>();
            Iterator<Rational> it = values.iterator();
            for (String core_unit : mixedUnitString.split("-and-")) {
                _measures.add(MeasureR.from(it.next(), core_unit));
            }
            // later, check validitity;
            // units are in comparable, in descending size,
            // number of values are the same as the number of units
            return new MixedMeasure(_measures);
        }

        public static final MixedMeasure from(MeasureR... values) {
            return new MixedMeasure(Arrays.asList(values));
        }

        public static final MixedMeasure from(List<MeasureR> values) {
            return new MixedMeasure(values);
        }

        @Override
        public String toString() {
            return Joiner.on(", ").join(measures);
        }

        @Override
        public boolean equals(Object obj) {
            MixedMeasure that = (MixedMeasure) obj;
            return measures.equals(that.measures);
        }

        @Override
        public int hashCode() {
            return measures.hashCode();
        }
    }
}
