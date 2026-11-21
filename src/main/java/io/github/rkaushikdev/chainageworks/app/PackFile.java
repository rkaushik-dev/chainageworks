package io.github.rkaushikdev.chainageworks.app;

import java.util.Objects;

/**
 * One file of a month end pack.
 *
 * @param name the file name, {@code certificate.md}
 * @param text what goes in it
 */
public record PackFile(String name, String text) {

    public PackFile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
    }
}
