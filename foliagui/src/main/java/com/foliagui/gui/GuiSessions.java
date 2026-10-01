package com.foliagui.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** The anvil, sign, merchant and chat-prompt sessions of one {@code FoliaGUIService}. */
@ApiStatus.Internal
public final class GuiSessions {

    final SessionRegistry<InputSession> input = new SessionRegistry<>();
    final SessionRegistry<AnvilGui> anvil = new SessionRegistry<>();
    final SessionRegistry<SignGui> sign = new SessionRegistry<>();
    final SessionRegistry<MerchantGui> merchant = new SessionRegistry<>();
    final SessionRegistry<ChatPrompt> chat = new SessionRegistry<>();
    volatile Listener chatHandler;
    private final java.util.Set<InputSession> managedInputs = new java.util.HashSet<>();
    private boolean closed;

    synchronized boolean trackInput(InputSession session) {
        if (closed) {
            return false;
        }
        managedInputs.add(session);
        return true;
    }

    synchronized boolean activateInput(InputSession session) {
        if (closed || session.isCancelled() || !managedInputs.contains(session)) {
            return false;
        }
        input.put(session.player, session);
        return true;
    }

    synchronized void forgetInput(InputSession session) {
        managedInputs.remove(session);
        input.remove(session.player, session);
    }

    private synchronized java.util.List<InputSession> managedInputs() {
        return java.util.List.copyOf(managedInputs);
    }

    public boolean hasAny(@NotNull HumanEntity player) {
        return anvil.has(player) || sign.has(player) || merchant.has(player) || chat.has(player);
    }

    public int anvilCount() {
        return anvil.size();
    }

    public int signCount() {
        return sign.size();
    }

    public int merchantCount() {
        return merchant.size();
    }

    public int chatCount() {
        return chat.size();
    }

    public void disconnect(org.bukkit.entity.Player player) {
        for (InputSession session : managedInputs()) {
            if (session.player.getUniqueId().equals(player.getUniqueId())) {
                session.finish(InputResult.ended(InputResult.Status.DISCONNECTED));
            }
        }
        anvil.remove(player);
        merchant.remove(player);
    }

    public void clearAll() {
        java.util.List<InputSession> ending;
        synchronized (this) {
            closed = true;
            ending = java.util.List.copyOf(managedInputs);
            managedInputs.clear();
        }
        for (InputSession session : ending) {
            session.finish(InputResult.ended(InputResult.Status.CANCELLED));
        }
        input.clear();
        anvil.clear();
        for (SignGui gui : sign.values()) {
            gui.cancelTimeout();
        }
        sign.clear();
        merchant.clear();
        ChatPrompt.clear(this);
    }
}
