package org.unicode.cldr.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.Freezable;
import com.ibm.icu.util.ICUException;

/**
 * Very basic class for rational numbers. No attempt to optimize, since it will just
 * be used for testing within CLDR.
 * 
 * @author markdavis
 *
 */
public final class Rational implements Comparable<Rational> {
    public final BigInteger numerator;
    public final BigInteger denominator;

    // Constraints:
    //   always stored in normalized form. 
    //   no common factor > 1 (reduced)
    //   denominator never negative
    //   if numerator is zero, denominator is 1 or 0
    //   if denominator is zero, numerator is 1, -1, or 0

    public static final Rational ZERO = Rational.of(0);
    public static final Rational ONE = Rational.of(1);
    public static final Rational NEGATIVE_ONE = Rational.of(-1);

    public static final Rational INFINITY = Rational.of(1,0);
    public static final Rational NEGATIVE_INFINITY = Rational.of(-1,0);
    public static final Rational NaN = Rational.of(0,0);


    public static class RationalParser implements Freezable<RationalParser>{
        private static Splitter slashSplitter = Splitter.on('/').trimResults();
        private static Splitter starSplitter = Splitter.on('*').trimResults();

        private Map<String,Rational> constants = new LinkedHashMap<>();
        
        public RationalParser addConstant(String id, String value) {
            if (constants.put(id, parse(value)) != null) {
                throw new IllegalArgumentException("Can't reset constant " + id + " = " + value);
            }
            return this;
        }

        /* 
         * input = comp (/ comp)?
         * comp = comp2 (* comp2)*
         * comp2 = digits (. digits)? | constant
         * */

        public Rational parse(String input) {
            List<String> comps = slashSplitter.splitToList(input);
            try {
                switch (comps.size()) {
                case 1: return process(comps.get(0));
                case 2: return process(comps.get(0)).divide(process(comps.get(1)));
                default: throw new IllegalArgumentException("too many slashes in " + input);
                }
            } catch (Exception e) {
                throw new ICUException("bad input: " + input, e);
            }
        }

        private  Rational process(String string) {
            Rational result = null;
            for (String comp : starSplitter.split(string)) {
                Rational ratComp = process2(comp);
                result = result == null ? ratComp : result.multiply(ratComp);
            }
            return result;
        }

        private Rational process2(String input) {
            if (input.charAt(0) > '9') {
                return constants.get(input);
            }
            return Rational.of(new BigDecimal(input));
//            BigInteger _numerator;
//            BigInteger _denominator;
//            int dotPos = input.indexOf('.');
//            if (dotPos >= 0) {
//                _numerator = new BigInteger(input.replace(".", ""));
//                _denominator = BigInteger.valueOf((long)Math.pow(10, input.length() - dotPos - 1));
//            } else {
//                _numerator = new BigInteger(input);
//                _denominator = BigInteger.ONE;
//            }
//            return new Rational(_numerator, _denominator);
        }

        boolean frozen = false;
        
        @Override
        public boolean isFrozen() {
            return frozen;
        }

        @Override
        public RationalParser freeze() {
            frozen = true;
            constants = ImmutableMap.copyOf(constants);
            return this;
        }

        @Override
        public RationalParser cloneAsThawed() {
            throw new UnsupportedOperationException();
        }
    }

    public static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static Rational of(long numerator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.ONE);
    }

    public static Rational of(BigInteger numerator, BigInteger denominator) {
        return new Rational(numerator, denominator);
    }

    public static Rational of(BigInteger numerator) {
        return new Rational(numerator, BigInteger.ONE);
    }

    private Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.compareTo(BigInteger.ZERO) < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.compareTo(BigInteger.ONE) > 0) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public Rational add(Rational other) {
        BigInteger gcd_den = denominator.gcd(other.denominator);
        return new Rational(
            numerator.multiply(other.denominator).divide(gcd_den)
            .add(other.numerator.multiply(denominator).divide(gcd_den)),
            denominator.multiply(other.denominator).divide(gcd_den)
            );
    }

    public Rational multiply(Rational other) {
        BigInteger gcd_num_oden = numerator.gcd(other.denominator);
        boolean isZero = gcd_num_oden.equals(BigInteger.ZERO);
        BigInteger smallNum = isZero ? numerator : numerator.divide(gcd_num_oden);
        BigInteger smallODen = isZero ? other.denominator : other.denominator.divide(gcd_num_oden);

        BigInteger gcd_den_onum = denominator.gcd(other.numerator);
        isZero = gcd_den_onum.equals(BigInteger.ZERO);
        BigInteger smallONum = isZero ? other.numerator : other.numerator.divide(gcd_den_onum);
        BigInteger smallDen = isZero ? denominator : denominator.divide(gcd_den_onum);

        return new Rational(smallNum.multiply(smallONum), smallDen.multiply(smallODen));
    }

    public Rational divide(Rational other) {
        return multiply(other.reciprocal());
    }

    public Rational reciprocal() {
        return new Rational(denominator, numerator);
    }

    public Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    public BigDecimal toBigDecimal(MathContext mathContext) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), mathContext);
    }
    
    public BigDecimal toBigDecimal() {
        return toBigDecimal(MathContext.UNLIMITED);
    }
    
    public static Rational of(double value) {
        return of(new BigDecimal(value));
    }

    public static Rational of(BigDecimal bigDecimal) {
        // scale()
        // If zero or positive, the scale is the number of digits to the right of the decimal point. 
        // If negative, the unscaled value of the number is multiplied by ten to the power of the negation of the scale. 
        // For example, a scale of -3 means the unscaled value is multiplied by 1000.
        final int scale = bigDecimal.scale();
        final BigInteger unscaled = bigDecimal.unscaledValue();
        if (scale == 0) {
            return new Rational(unscaled, BigInteger.ONE);
        } else if (scale >= 0) {
            return new Rational(unscaled, BigDecimal.ONE.movePointRight(scale).toBigInteger());
        } else {
            return new Rational(unscaled.multiply(BigDecimal.ONE.movePointLeft(scale).toBigInteger()), BigInteger.ONE);
        }
    }

    @Override
    public String toString() {
        // could also return as "exact" decimal, if only factors of the denominator are 2 and 5
        return numerator + (denominator.equals(BigInteger.ONE) ? "" : "/" + denominator);
    }

    @Override
    public int compareTo(Rational other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    public boolean equals(Object that) {
        return equals((Rational)that); // TODO fix later
    }

    public boolean equals(Rational that) {
        return numerator.equals(that.numerator)
            && denominator.equals(that.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

}