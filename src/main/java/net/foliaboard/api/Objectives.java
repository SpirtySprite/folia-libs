package net.foliaboard.api;

import org.jetbrains.annotations.NotNull;

/** The numbers shown under player names and in the tab list. Obtain it from {@code FoliaBoard#objectives()}. */
public interface Objectives {

    @NotNull ScoreObjective belowName();

    @NotNull ScoreObjective tabList();
}
