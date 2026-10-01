package com.foliagui.gui;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;
import java.util.Optional;

/** A terminal input outcome shared by chat, sign and anvil prompts. */
@ApiStatus.Experimental
public record InputResult(Status status, Optional<String> text) {
    /** Terminal outcomes; cancelled includes explicit cancellation, replacement and service shutdown. */
    public enum Status { SUBMITTED, CANCELLED, TIMED_OUT, DISCONNECTED, UNSUPPORTED, FAILED }

    /** Validates that only submitted input carries text. */
    public InputResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(text, "text");
        if ((status == Status.SUBMITTED) != text.isPresent()) {
            throw new IllegalArgumentException("Only submitted input carries text");
        }
    }

    /** Creates a submitted outcome. */
    public static InputResult submitted(String text) {
        return new InputResult(Status.SUBMITTED, Optional.of(text));
    }

    /** Creates a terminal outcome without text. */
    public static InputResult ended(Status status) {
        return new InputResult(status, Optional.empty());
    }
}
