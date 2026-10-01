package com.foliagui.gui;

import org.jetbrains.annotations.ApiStatus;

/** The outcome of a scheduled GUI operation. Execution failures complete the operation future exceptionally. */
@ApiStatus.Experimental
public enum GuiOperationResult {
    OPENED, CLOSED, REJECTED, RETIRED, SUPERSEDED
}
