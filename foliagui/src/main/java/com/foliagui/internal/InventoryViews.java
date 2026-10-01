package com.foliagui.internal;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Method;

public final class InventoryViews {
    private static final Method TOP = topMethod();

    private InventoryViews() {
    }

    public static Inventory top(InventoryView view) {
        try {
            return (Inventory) TOP.invoke(view);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read inventory view", failure);
        }
    }

    private static Method topMethod() {
        try {
            return InventoryView.class.getMethod("getTopInventory");
        } catch (NoSuchMethodException missing) {
            throw new ExceptionInInitializerError(missing);
        }
    }
}
