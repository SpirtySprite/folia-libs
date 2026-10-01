package net.foliaboard.internal.service;

import net.foliaboard.api.Objectives;
import net.foliaboard.api.ScoreObjective;
import net.foliaboard.internal.Ids;
import net.foliaboard.internal.objective.ScoreObjectiveImpl;
import net.foliaboard.internal.packet.DisplaySlotType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ObjectiveService implements Objectives {
    private final BoardRuntime runtime;
    private volatile ScoreObjectiveImpl belowName;
    private volatile ScoreObjectiveImpl tabList;

    public ObjectiveService(@NotNull BoardRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public @NotNull ScoreObjective belowName() {
        runtime.ensureOpen();
        ScoreObjectiveImpl local = belowName;
        if (local == null) {
            synchronized (this) {
                if (belowName == null) {
                    belowName = new ScoreObjectiveImpl(runtime.plugin(), runtime.adapter(),
                            Ids.belowNameObjective(runtime.namespace()), DisplaySlotType.BELOW_NAME);
                    belowName.cleanupPlugin(runtime.cleanupPlugin());
                    belowName.metrics(runtime.metrics());
                }
                local = belowName;
            }
        }
        return local;
    }

    @Override
    public @NotNull ScoreObjective tabList() {
        runtime.ensureOpen();
        ScoreObjectiveImpl local = tabList;
        if (local == null) {
            synchronized (this) {
                if (tabList == null) {
                    tabList = new ScoreObjectiveImpl(runtime.plugin(), runtime.adapter(),
                            Ids.tabListObjective(runtime.namespace()), DisplaySlotType.PLAYER_LIST);
                    tabList.cleanupPlugin(runtime.cleanupPlugin());
                    tabList.metrics(runtime.metrics());
                }
                local = tabList;
            }
        }
        return local;
    }

    public void onJoin(@NotNull Player player) {
        ScoreObjectiveImpl below = belowName;
        if (below != null) {
            below.onJoin(player);
        }
        ScoreObjectiveImpl tab = tabList;
        if (tab != null) {
            tab.onJoin(player);
        }
    }

    public void onQuit(@NotNull Player player) {
        ScoreObjectiveImpl below = belowName;
        if (below != null) {
            below.onQuit(player);
        }
        ScoreObjectiveImpl tab = tabList;
        if (tab != null) {
            tab.onQuit(player);
        }
    }

    public void closeAll() {
        ScoreObjectiveImpl below = belowName;
        if (below != null) {
            below.closeAll();
        }
        ScoreObjectiveImpl tab = tabList;
        if (tab != null) {
            tab.closeAll();
        }
    }
}
