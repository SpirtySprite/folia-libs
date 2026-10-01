package net.foliaboard.api;

import net.foliaboard.internal.service.SidebarService;
import org.jetbrains.annotations.ApiStatus;
import net.foliaboard.api.animation.Animation;
import net.foliaboard.api.format.NumberFormat;
import net.foliacommons.text.Legacy;
import net.foliaboard.api.text.Text;
import net.foliaboard.internal.scheduler.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Objects;
import java.util.function.IntConsumer;
import net.foliaboard.internal.board.SidebarImpl;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BoardBuilder {
    private record LineSpec(Function<Player, Component> renderer, NumberFormat format, boolean dynamic,
                            Predicate<Player> condition) {
        LineSpec(Function<Player, Component> renderer, NumberFormat format, boolean dynamic) {
            this(renderer, format, dynamic, null);
        }
    }

    private static final int ANIMATION_REFRESH_TICKS = 3;
    private static final int PLACEHOLDER_REFRESH_TICKS = 20;

    private final SidebarService board;
    private final Player player;

    private Function<Player, Component> titleRenderer = p -> Component.empty();
    private boolean titleDynamic = false;
    private final TreeMap<Integer, LineSpec> lines = new TreeMap<>();
    private final java.util.List<net.foliaboard.api.hook.LineProcessor> processors = new java.util.ArrayList<>();
    private int nextAutoIndex = 0;
    private boolean parsePlaceholders = false;
    private int refreshTicks = -1;
    private int titleRefreshTicks = -1;
    private final Map<Integer, Integer> lineRefreshTicks = new HashMap<>();
    private final Map<Integer, Component> rendered = new HashMap<>();
    private final Map<Integer, Boolean> conditions = new HashMap<>();
    private final Map<Integer, Long> nextRefresh = new HashMap<>();
    private long elapsedTicks;

    @ApiStatus.Internal
    public BoardBuilder(@NotNull SidebarService board, @NotNull Player player) {
        this.board = board;
        this.player = player;
    }

    public synchronized @NotNull BoardBuilder placeholders(boolean enabled) {
        this.parsePlaceholders = enabled;
        return this;
    }

    public synchronized @NotNull BoardBuilder refreshEvery(int ticks) {
        this.refreshTicks = Math.max(1, ticks);
        return this;
    }

    public synchronized @NotNull BoardBuilder processor(@NotNull net.foliaboard.api.hook.LineProcessor processor) {
        this.processors.add(processor);
        return this;
    }

    public synchronized @NotNull BoardBuilder title(@NotNull String miniMessage) {
        Rendered r = render(miniMessage);
        this.titleRenderer = r.renderer;
        this.titleDynamic = r.dynamic;
        return this;
    }

    public synchronized @NotNull BoardBuilder title(@NotNull ComponentLike title) {
        Component c = title.asComponent();
        this.titleRenderer = p -> c;
        this.titleDynamic = false;
        return this;
    }

    public synchronized @NotNull BoardBuilder title(@NotNull Animation<Component> animation) {
        this.titleRenderer = new AnimatedRenderer(animation);
        this.titleDynamic = true;
        return this;
    }

    public synchronized @NotNull BoardBuilder title(@NotNull Function<Player, String> perPlayer) {
        this.titleRenderer = new DynamicRenderer(perPlayer);
        this.titleDynamic = true;
        return this;
    }

    public synchronized @NotNull BoardBuilder line(int index, @NotNull String miniMessage) {
        checkIndex(index);
        Rendered r = render(miniMessage);
        lines.put(index, new LineSpec(r.renderer, null, r.dynamic));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(int index, @NotNull String miniMessage, @NotNull NumberFormat format) {
        checkIndex(index);
        Rendered r = render(miniMessage);
        lines.put(index, new LineSpec(r.renderer, format, r.dynamic));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(int index, @NotNull ComponentLike component) {
        checkIndex(index);
        Component c = component.asComponent();
        lines.put(index, new LineSpec(p -> c, null, false));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(int index, @NotNull Animation<Component> animation) {
        checkIndex(index);
        lines.put(index, new LineSpec(new AnimatedRenderer(animation), null, true));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(int index, @NotNull Function<Player, String> perPlayer) {
        checkIndex(index);
        lines.put(index, new LineSpec(new DynamicRenderer(perPlayer), null, true));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(@NotNull Function<Player, String> perPlayer) {
        return line(nextIndex(), perPlayer);
    }

    public synchronized @NotNull BoardBuilder lineIf(@NotNull Predicate<Player> condition, @NotNull String anyFormat) {
        int index = nextIndex();
        Rendered r = render(anyFormat);
        lines.put(index, new LineSpec(r.renderer, null, true, Objects.requireNonNull(condition, "condition")));
        return this;
    }

    public synchronized @NotNull BoardBuilder lineIf(@NotNull Predicate<Player> condition, @NotNull Function<Player, String> perPlayer) {
        lines.put(nextIndex(), new LineSpec(new DynamicRenderer(perPlayer), null, true, condition));
        return this;
    }

    public synchronized @NotNull BoardBuilder line(@NotNull String miniMessage) {
        return line(nextIndex(), miniMessage);
    }

    public synchronized @NotNull BoardBuilder line(@NotNull String miniMessage, @NotNull NumberFormat format) {
        return line(nextIndex(), miniMessage, format);
    }

    public synchronized @NotNull BoardBuilder line(@NotNull ComponentLike component) {
        return line(nextIndex(), component);
    }

    public synchronized @NotNull BoardBuilder line(@NotNull Animation<Component> animation) {
        return line(nextIndex(), animation);
    }

    public synchronized @NotNull BoardBuilder blankLine() {
        return line(nextIndex(), Component.empty());
    }

    public synchronized @NotNull BoardBuilder lines(@NotNull String... miniMessageLines) {
        for (String line : miniMessageLines) {
            line(nextIndex(), line);
        }
        return this;
    }

    public synchronized @NotNull BoardBuilder lines(@NotNull Iterable<String> miniMessageLines) {
        for (String line : miniMessageLines) {
            line(nextIndex(), line);
        }
        return this;
    }

    /** Sets the independent refresh cadence for a row. Callbacks execute on the player's thread. */
    @ApiStatus.Experimental
    public synchronized @NotNull BoardBuilder lineRefreshEvery(int index, int ticks) {
        checkIndex(index);
        lineRefreshTicks.put(index, Math.max(1, ticks));
        return this;
    }

    /** Sets the title cadence independently of expensive row renderers. */
    @ApiStatus.Experimental
    public synchronized @NotNull BoardBuilder titleRefreshEvery(int ticks) {
        titleRefreshTicks = Math.max(1, ticks);
        return this;
    }

    /** Appends a reusable section to this recipe. Configuration is frozen when built. */
    @ApiStatus.Experimental
    public synchronized @NotNull BoardBuilder section(net.foliaboard.api.layout.LayoutSection section) {
        Objects.requireNonNull(section, "section").apply(this);
        return this;
    }

    public synchronized @NotNull Sidebar build() {
        BoardBuilder frozen = freeze();
        board.markBuilderOwned(player);
        board.trackRecipe(player, frozen::build);
        SidebarImpl sidebar = (SidebarImpl) board.sidebar(player);
        sidebar.lineProcessors(List.copyOf(processors));
        int maxIndex = frozen.lines.isEmpty() ? -1 : frozen.lines.lastKey();
        IntConsumer refresh = request -> frozen.paint(sidebar, maxIndex, request);
        sidebar.refreshAction(refresh);
        Runnable apply = () -> {
            if (sidebar.ownsRefresh(refresh)) {
                refresh.accept(Integer.MIN_VALUE);
            }
        };
        Schedulers.onEntity(board.runtime().plugin(), player, apply);
        boolean dynamic = titleDynamic || lines.values().stream().anyMatch(LineSpec::dynamic);
        if (dynamic) {
            int interval = frozen.smallestInterval();
            board.trackRefresh(player, Schedulers.entityTimer(board.runtime().plugin(), player, handle -> {
                if (sidebar.closed() || !player.isOnline() || !sidebar.ownsRefresh(refresh)) {
                    handle.cancel();
                    return;
                }
                frozen.elapsedTicks += interval;
                board.recordRefresh();
                refresh.accept(Integer.MAX_VALUE);
            }, interval, interval));
        } else {
            board.trackRefresh(player, null);
        }
        return sidebar;
    }

    private BoardBuilder freeze() {
        BoardBuilder copy = new BoardBuilder(board, player);
        copy.parsePlaceholders = parsePlaceholders;
        copy.refreshTicks = refreshTicks;
        copy.titleRefreshTicks = titleRefreshTicks;
        copy.titleRenderer = copy.copyRenderer(titleRenderer);
        copy.titleDynamic = titleDynamic;
        copy.lineRefreshTicks.putAll(lineRefreshTicks);
        copy.processors.addAll(processors);
        copy.nextAutoIndex = nextAutoIndex;
        lines.forEach((index, spec) -> copy.lines.put(index,
                new LineSpec(copy.copyRenderer(spec.renderer()), spec.format(), spec.dynamic(), spec.condition())));
        return copy;
    }

    private Function<Player, Component> copyRenderer(Function<Player, Component> renderer) {
        if (renderer instanceof DynamicRenderer dynamic) {
            return new DynamicRenderer(dynamic.source);
        }
        if (renderer instanceof CachingRenderer cached) {
            return new CachingRenderer(cached.raw);
        }
        return renderer;
    }

    private int smallestInterval() {
        int interval = titleDynamic ? interval(-1, titleRenderer) : Integer.MAX_VALUE;
        for (Map.Entry<Integer, LineSpec> row : lines.entrySet()) {
            if (row.getValue().dynamic()) {
                interval = interval == Integer.MAX_VALUE ? interval(row.getKey(), row.getValue().renderer())
                        : commonInterval(interval, interval(row.getKey(), row.getValue().renderer()));
            }
        }
        return interval == Integer.MAX_VALUE ? PLACEHOLDER_REFRESH_TICKS : interval;
    }

    private static int commonInterval(int first, int second) {
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    private int interval(int index, Function<Player, Component> renderer) {
        int explicit = index == -1 ? titleRefreshTicks : lineRefreshTicks.getOrDefault(index, -1);
        return explicit > 0 ? explicit : refreshTicks > 0 ? refreshTicks
                : renderer instanceof AnimatedRenderer ? ANIMATION_REFRESH_TICKS : PLACEHOLDER_REFRESH_TICKS;
    }

    private void paint(Sidebar sidebar, int maxIndex, int request) {
        if (sidebar.closed() || !player.isOnline()) {
            return;
        }
        if (request == Integer.MAX_VALUE && !due(-1, titleDynamic, request)
                && lines.entrySet().stream().noneMatch(row -> due(row.getKey(), row.getValue().dynamic(), request))) {
            return;
        }
        if (due(-1, titleDynamic, request)) {
            renderSafely(-1, titleRenderer);
        }
        List<SidebarState.Line> frame = new ArrayList<>();
        for (int index = 0; index <= maxIndex; index++) {
            LineSpec spec = lines.get(index);
            if (spec == null) {
                frame.add(new SidebarState.Line(Component.empty()));
                continue;
            }
            if (due(index, spec.dynamic(), request)) {
                try {
                    conditions.put(index, spec.condition() == null || spec.condition().test(player));
                    nextRefresh.put(index, elapsedTicks + interval(index, spec.renderer()));
                    if (conditions.get(index)) {
                        renderSafely(index, spec.renderer());
                    }
                } catch (RuntimeException failure) {
                    warn(index, failure);
                }
            }
            if (conditions.getOrDefault(index, false)) {
                frame.add(new SidebarState.Line(rendered.getOrDefault(index, Component.empty()), Optional.ofNullable(spec.format())));
            }
        }
        sidebar.replace(new SidebarState(rendered.getOrDefault(-1, Component.empty()), frame, sidebar.visible()));
    }

    private boolean due(int index, boolean dynamic, int request) {
        return request == Integer.MIN_VALUE || request == index || request == Integer.MAX_VALUE
                && (!nextRefresh.containsKey(index) || dynamic && elapsedTicks >= nextRefresh.get(index));
    }

    private void renderSafely(int index, Function<Player, Component> source) {
        try {
            rendered.put(index, Objects.requireNonNull(source.apply(player), "renderer result"));
        } catch (RuntimeException failure) {
            warn(index, failure);
        } finally {
            nextRefresh.put(index, elapsedTicks + interval(index, source));
        }
    }

    private void warn(int index, RuntimeException failure) {
        board.runtime().plugin().getLogger().log(java.util.logging.Level.WARNING,
                "FoliaBoard renderer failed for row " + index + "; preserving its previous value", failure);
    }

    private int nextIndex() {
        checkIndex(nextAutoIndex);
        return nextAutoIndex++;
    }

    private static void checkIndex(int index) {
        if (index < 0 || index > 63) {
            throw new IllegalArgumentException("Line index must be 0 to 63");
        }
    }

    private record AnimatedRenderer(Animation<Component> animation) implements Function<Player, Component> {
        @Override
        public Component apply(Player player) {
            return animation.current();
        }
    }

    private final class DynamicRenderer implements Function<Player, Component> {
        private final Function<Player, String> source;

        private DynamicRenderer(Function<Player, String> source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override
        public Component apply(Player viewer) {
            return resolve(viewer, source.apply(viewer));
        }
    }

    private record Rendered(Function<Player, Component> renderer, boolean dynamic) {
    }

    private Rendered render(String raw) {
        String template = Legacy.toMini(raw);
        if (parsePlaceholders && template.indexOf('%') >= 0) {
            return new Rendered(new CachingRenderer(template), true);
        }
        Component parsed = Text.mini(template);
        return new Rendered(p -> parsed, false);
    }

    private Component resolve(Player viewer, String raw) {
        if (raw == null) {
            return Component.empty();
        }
        String template = Legacy.toMini(raw);
        if (parsePlaceholders && template.indexOf('%') >= 0) {
            template = board.runtime().placeholders().resolveForMiniMessage(viewer, template);
        }
        return Text.cached(template);
    }

    private final class CachingRenderer implements Function<Player, Component> {
        private final String raw;
        private String lastResolved;
        private Component lastComponent;

        CachingRenderer(String raw) {
            this.raw = raw;
        }

        @Override
        public Component apply(Player player) {
            String resolved = parsePlaceholders ? board.runtime().placeholders().resolveForMiniMessage(player, raw) : raw;
            if (!resolved.equals(lastResolved)) {
                lastResolved = resolved;
                lastComponent = Text.mini(resolved);
            }
            return lastComponent;
        }
    }
}
