package com.foliagui.gui;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Pure validation returning a visible error message, or empty when input is valid. */
@ApiStatus.Experimental
@FunctionalInterface
public interface InputValidator {
    /** Validates on the player's owning thread; expensive work belongs in a separate asynchronous operation. */
    Optional<String> validate(String text);

    /** Requires at least one nonwhitespace character. */
    static InputValidator nonblank(String error) {
        return matching(text -> !text.isBlank(), error);
    }

    /** Requires a character count within inclusive bounds. */
    static InputValidator length(int minimum, int maximum, String error) {
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException("Invalid length bounds");
        }
        return matching(text -> text.codePointCount(0, text.length()) >= minimum
                && text.codePointCount(0, text.length()) <= maximum, error);
    }

    /** Requires a finite decimal number within inclusive bounds. */
    static InputValidator number(double minimum, double maximum, String error) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid numeric bounds");
        }
        return matching(text -> {
            try {
                double value = Double.parseDouble(text.trim());
                return Double.isFinite(value) && value >= minimum && value <= maximum;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }, error);
    }

    /** Adapts a custom acceptance predicate. */
    static InputValidator matching(Predicate<String> predicate, String error) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(error, "error");
        return text -> predicate.test(text) ? Optional.empty() : Optional.of(error);
    }

    /** Checks this validator first, then another validator if accepted. */
    default InputValidator and(InputValidator next) {
        Objects.requireNonNull(next, "next");
        return text -> validate(text).or(() -> next.validate(text));
    }
}
