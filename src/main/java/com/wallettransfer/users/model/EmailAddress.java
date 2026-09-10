package com.wallettransfer.users.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public EmailAddress {
        Objects.requireNonNull(value, "email must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("email must be valid");
        }
    }
}
