package io.github.rkaushikdev.chainageworks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A value or the reason there is none.
 *
 * <p>Parsing site paperwork is the one place where throwing is wrong: an import run has to report
 * every bad row, not stop on the first one. Those paths return a {@code Result} and the callers
 * collect the failures.
 *
 * @param <T> what a success holds
 * @param <E> what a failure holds
 */
public sealed interface Result<T, E> {

    /** A success. */
    record Ok<T, E>(T value) implements Result<T, E> {}

    /** A failure. */
    record Err<T, E>(E error) implements Result<T, E> {}

    /** The rows that parsed and the ones that did not, each half in its original order. */
    record Partition<T, E>(List<T> values, List<E> errors) {

        public Partition {
            values = List.copyOf(values);
            errors = List.copyOf(errors);
        }
    }

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok;
    }

    default boolean isErr() {
        return this instanceof Err;
    }

    /** The value of a success. Throws {@link IllegalStateException} on a failure. */
    default T value() {
        if (this instanceof Ok<T, E> good) {
            return good.value();
        }
        throw new IllegalStateException("a failed result has no value");
    }

    /** The error of a failure. Throws {@link IllegalStateException} on a success. */
    default E error() {
        if (this instanceof Err<T, E> bad) {
            return bad.error();
        }
        throw new IllegalStateException("a successful result has no error");
    }

    /** Apply {@code f} to a value; a failure passes through and {@code f} is not called. */
    default <U> Result<U, E> map(Function<? super T, ? extends U> f) {
        return switch (this) {
            case Ok<T, E> good -> new Ok<>(f.apply(good.value()));
            case Err<T, E> bad -> new Err<>(bad.error());
        };
    }

    /** Chain a second fallible step onto a value. */
    default <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> f) {
        return switch (this) {
            case Ok<T, E> good -> f.apply(good.value());
            case Err<T, E> bad -> new Err<>(bad.error());
        };
    }

    /** The value, or {@code fallback} on a failure. */
    default T orElse(T fallback) {
        return this instanceof Ok<T, E> good ? good.value() : fallback;
    }

    /** The value of a success, or the failure it holds thrown. */
    static <T> T unwrap(Result<T, ? extends CwException> result) {
        return switch (result) {
            case Ok<T, ? extends CwException> good -> good.value();
            case Err<T, ? extends CwException> bad -> throw bad.error();
        };
    }

    /** Split a batch into the values and the errors, keeping the order inside each half. */
    static <T, E> Partition<T, E> partition(List<? extends Result<T, E>> results) {
        List<T> values = new ArrayList<>();
        List<E> errors = new ArrayList<>();
        for (Result<T, E> r : results) {
            switch (r) {
                case Ok<T, E> good -> values.add(good.value());
                case Err<T, E> bad -> errors.add(bad.error());
            }
        }
        return new Partition<>(values, errors);
    }

    /** All or nothing: the first failure wins and the rest are not reported. */
    static <T, E> Result<List<T>, E> collect(List<? extends Result<T, E>> results) {
        List<T> values = new ArrayList<>();
        for (Result<T, E> r : results) {
            switch (r) {
                case Ok<T, E> good -> values.add(good.value());
                case Err<T, E> bad -> {
                    return new Err<>(bad.error());
                }
            }
        }
        return new Ok<>(List.copyOf(values));
    }
}
