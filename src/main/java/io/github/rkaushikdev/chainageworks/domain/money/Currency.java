package io.github.rkaushikdev.chainageworks.domain.money;

import java.util.Optional;

/** The three currencies the contracts are written in. Each is paid in hundredths. */
public enum Currency {
    GBP("£"),
    EUR("€"),
    USD("$");

    private final String symbol;

    Currency(String symbol) {
        this.symbol = symbol;
    }

    /** The sign written in front of an amount. */
    public String symbol() {
        return symbol;
    }

    /** Places of minor units: two for all three. */
    public int minorDigits() {
        return 2;
    }

    /** Minor units in one major unit: a hundred pence to the pound. */
    public long minorUnitsPerMajor() {
        return 100;
    }

    /** The three-letter code. */
    public String code() {
        return name();
    }

    /** True for exactly one of the three codes, upper case. */
    public static boolean isCode(String value) {
        return fromCode(value).isPresent();
    }

    /** The currency for an exact upper case code. */
    public static Optional<Currency> fromCode(String value) {
        for (Currency currency : values()) {
            if (currency.name().equals(value)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }

    /** The currency a symbol stands for. */
    public static Optional<Currency> fromSymbol(String symbol) {
        for (Currency currency : values()) {
            if (currency.symbol.equals(symbol)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }
}
