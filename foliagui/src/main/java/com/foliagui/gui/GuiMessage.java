package com.foliagui.gui;

import org.jetbrains.annotations.ApiStatus;

/** Configurable built-in messages. Numeric substitutions use {0}, {1} and subsequent argument positions. */
@ApiStatus.Experimental
public enum GuiMessage {
    BACK("&eBack"), CLOSE("&cClose"), PREVIOUS("&ePrevious page"), NEXT("&eNext page"),
    PAGE("&fPage &e{0}&7/&e{1}"), LOADING("&7Loading..."), LOAD_FAILED("&cCould not load this menu"),
    PAGE_PROMPT("&eType a page number (1-{0}):"), INVALID_NUMBER("&cThat's not a number."),
    SEARCH_PROMPT("&eType a search term (or 'clear'):"), SEARCH_CLEAR("clear"),
    QUANTITY_TITLE("&8Choose an amount"), AMOUNT("&7Amount: &f{0}"),
    CONFIRM("&aConfirm"), CANCEL("&cCancel"), INVALID_INPUT("&cInvalid input. Please try again.");

    private final String text;

    GuiMessage(String text) {
        this.text = text;
    }

    /** Returns the default formatted template for this message. */
    public String defaultText() {
        return text;
    }
}
