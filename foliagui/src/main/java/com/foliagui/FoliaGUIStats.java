package com.foliagui;

/**
 * A snapshot of what one {@link FoliaGUIService} is currently doing.
 *
 * @param openGuis         inventory GUIs that players have open
 * @param anvilSessions    open anvil text inputs
 * @param signSessions     open sign text inputs
 * @param merchantSessions open merchant windows
 * @param chatPrompts      players being asked to type in chat
 */
public record FoliaGUIStats(int openGuis, int anvilSessions, int signSessions, int merchantSessions,
                            int chatPrompts) {

    /** Everything that holds a player's attention right now. */
    public int total() {
        return openGuis + anvilSessions + signSessions + merchantSessions + chatPrompts;
    }
}
