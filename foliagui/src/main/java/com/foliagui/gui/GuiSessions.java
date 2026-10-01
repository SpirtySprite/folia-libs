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
        InputSession session = input.get(player);
        if (session != null) {
            session.finish(InputResult.ended(InputResult.Status.DISCONNECTED));
        }
        anvil.remove(player);
        merchant.remove(player);
    }

    public void clearAll() {
        for (InputSession session : input.values()) {
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
