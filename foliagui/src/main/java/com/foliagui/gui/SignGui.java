package com.foliagui.gui;

import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.scheduler.TaskHandle;
import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SignGui {

    private static final Side SIDE = Side.FRONT;
    private static final BlockData SIGN_BLOCK = Material.OAK_SIGN.createBlockData();

    private final FoliaGUIService service;
    private final List<Component> lines;
    private final List<String> initialText;
    private final Function<Player, Location> position;
    private final BiConsumer<Player, List<String>> onComplete;
    private final long timeoutTicks;

    private volatile Location openedAt;
    private volatile BlockData original;
    private volatile TaskHandle timeoutTask;

    private SignGui(Builder builder) {
        this.service = builder.service;
        this.lines = List.copyOf(builder.lines);
        this.initialText = builder.lines.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.toList());
        this.position = builder.position;
        this.onComplete = builder.onComplete;
        this.timeoutTicks = builder.timeoutTicks;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static boolean hasSession(@NotNull HumanEntity player) {
        return FoliaGUI.isInitialised() && hasSession(FoliaGUI.service(), player);
    }

    public static boolean hasSession(@NotNull FoliaGUIService service, @NotNull HumanEntity player) {
        return service.sessions().sign.has(player);
    }

    private @NotNull FoliaGUIService service() {
        return service != null ? service : FoliaGUI.service();
    }

    void cancelTimeout() {
        TaskHandle pending = timeoutTask;
        if (pending != null) {
            pending.cancel();
        }
    }

    public void open(@NotNull Player player) {
        openAsync(player);
    }

    /** Opens on the player's owning thread and reports unsupported presentation failures exceptionally. */
    @ApiStatus.Experimental
    public java.util.concurrent.CompletableFuture<GuiOperationResult> openAsync(@NotNull Player player) {
        var result = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        FoliaGUIService owner = service();
        if (owner.isClosed()) {
            result.complete(GuiOperationResult.REJECTED);
            return result;
        }
        owner.scheduler().runForEntity(player, () -> prepare(player, owner, result),
                () -> result.complete(GuiOperationResult.RETIRED));
        return result;
    }

    private void prepare(Player player, FoliaGUIService owner,
                         java.util.concurrent.CompletableFuture<GuiOperationResult> result) {
        try {
            if (owner.isClosed()) {
                result.complete(GuiOperationResult.REJECTED);
                return;
            }
            Location pos = java.util.Objects.requireNonNull(position.apply(player), "position").clone();
            SignGui previous = owner.sessions().sign.put(player, this);
            if (previous != null && previous != this) {
                previous.cancelTimeout();
                previous.revert(player);
            }
            if (!owner.scheduler().tryRunForLocation(pos, () -> capture(player, owner, pos, result))) {
                owner.sessions().sign.remove(player, this);
                result.complete(GuiOperationResult.REJECTED);
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private void capture(Player player, FoliaGUIService owner, Location pos,
                         java.util.concurrent.CompletableFuture<GuiOperationResult> result) {
        try {
            BlockData originalBlock = pos.getBlock().getBlockData();
            owner.scheduler().runForEntity(player, () -> present(player, owner, pos, originalBlock, result),
                    () -> result.complete(GuiOperationResult.RETIRED));
        } catch (RuntimeException failure) {
            owner.sessions().sign.remove(player, this);
            result.completeExceptionally(failure);
        }
    }

    private void present(Player player, FoliaGUIService owner, Location pos, BlockData originalBlock,
                         java.util.concurrent.CompletableFuture<GuiOperationResult> result) {
        try {
            if (owner.isClosed() || owner.sessions().sign.get(player) != this) {
                result.complete(GuiOperationResult.SUPERSEDED);
                return;
            }
            openedAt = pos;
            original = originalBlock;
            player.sendBlockChange(pos, SIGN_BLOCK);
            player.sendSignChange(pos, lines);
            player.openVirtualSign(pos, SIDE);
            if (timeoutTicks > 0) {
                timeoutTask = owner.scheduler().runForEntityTimer(player, () -> {
                    if (owner.sessions().sign.remove(player, this)) {
                        cancelTimeout();
                        revert(player);
                    }
                }, null, timeoutTicks, timeoutTicks);
            }
            result.complete(GuiOperationResult.OPENED);
        } catch (RuntimeException | LinkageError failure) {
            owner.sessions().sign.remove(player, this);
            revert(player);
            result.completeExceptionally(failure);
        }
    }

    void cancel(Player player) {
        if (service().sessions().sign.remove(player, this)) {
            cancelTimeout();
            if (org.bukkit.Bukkit.getServer().isOwnedByCurrentRegion(player)) {
                revert(player);
            } else {
                service().scheduler().runForEntity(player, () -> revert(player), null);
            }
        }
    }

    @ApiStatus.Internal
    public static boolean handleSignChange(@NotNull FoliaGUIService service, @NotNull Player player,
                                           @NotNull List<? extends net.kyori.adventure.text.Component> lines) {
        SignGui gui = service.sessions().sign.remove(player);
        if (gui == null) {
            return false;
        }
        if (gui.timeoutTask != null) {
            gui.timeoutTask.cancel();
        }
        List<String> raw = lines.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.toList());
        List<String> text = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            boolean untouched = i < gui.initialText.size() && line.equals(gui.initialText.get(i));
            text.add(untouched ? "" : line);
        }
        gui.revert(player);
        gui.onComplete.accept(player, text);
        return true;
    }

    @ApiStatus.Internal
    public static void handleQuit(@NotNull FoliaGUIService service, @NotNull Player player) {
        SignGui gui = service.sessions().sign.remove(player);
        if (gui != null && gui.timeoutTask != null) {
            gui.timeoutTask.cancel();
        }
    }

    public static void clearSessions() {
        if (FoliaGUI.isInitialised()) {
            GuiSessions sessions = FoliaGUI.service().sessions();
            for (SignGui gui : sessions.sign.values()) {
                gui.cancelTimeout();
            }
            sessions.sign.clear();
        }
    }

    private void revert(@NotNull Player player) {
        if (openedAt != null && player.getWorld() == openedAt.getWorld()) {
            player.sendBlockChange(openedAt, original != null ? original : SIGN_BLOCK);
        }
    }

    public static final class Builder {
        private FoliaGUIService service;

        public @NotNull Builder service(@NotNull FoliaGUIService service) {
            this.service = service;
            return this;
        }

        private List<Component> lines = defaultLines();
        private Function<Player, Location> position = player -> {
            Location below = player.getLocation().add(0, -3, 0);
            int floor = player.getWorld().getMinHeight();
            if (below.getBlockY() < floor) {
                below.setY(floor);
            }
            return below;
        };
        private BiConsumer<Player, List<String>> onComplete = (player, text) -> {
        };
        private long timeoutTicks = 20L * 60;

        private static @NotNull List<Component> defaultLines() {
            List<Component> lines = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                lines.add(Component.empty());
            }
            return lines;
        }

        public @NotNull Builder lines(@NotNull String... lines) {
            List<Component> parsed = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                parsed.add(i < lines.length ? Text.of(lines[i]) : Component.empty());
            }
            this.lines = parsed;
            return this;
        }

        public @NotNull Builder line(int lineNumber, @NotNull String text) {
            if (lineNumber < 1 || lineNumber > 4) {
                throw new IllegalArgumentException("lineNumber must be 1-4, was " + lineNumber);
            }
            this.lines.set(lineNumber - 1, Text.of(text));
            return this;
        }

        public @NotNull Builder position(@NotNull Function<Player, Location> position) {
            this.position = position;
            return this;
        }

        public @NotNull Builder onComplete(@NotNull BiConsumer<Player, List<String>> onComplete) {
            this.onComplete = onComplete;
            return this;
        }

        public @NotNull Builder timeout(long timeoutTicks) {
            this.timeoutTicks = timeoutTicks;
            return this;
        }

        public @NotNull SignGui build() {
            return new SignGui(this);
        }

        public void open(@NotNull Player player) {
            build().open(player);
        }
    }
}
