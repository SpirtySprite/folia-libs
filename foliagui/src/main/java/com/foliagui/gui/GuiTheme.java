package com.foliagui.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.item.GuiItem;
import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public final class GuiTheme {

    private static final Component BLANK_NAME = Text.label(" ");
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private volatile Function<GuiMessage, String> messages = GuiMessage::defaultText;

    public record ThemeSound(@NotNull Sound sound, float volume, float pitch) {
        public void play(@NotNull HumanEntity viewer) {
            if (!org.bukkit.Bukkit.getServer().isOwnedByCurrentRegion(viewer)) {
                play(com.foliagui.FoliaGUI.service(), viewer);
                return;
            }
            if (viewer instanceof Player player) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }

        /** Plays on the viewer's owning thread through the specified service. */
        @org.jetbrains.annotations.ApiStatus.Experimental
        public void play(@NotNull com.foliagui.FoliaGUIService service, @NotNull HumanEntity viewer) {
            service.scheduler().runForEntity(viewer, () -> play(viewer), null);
        }
    }

    private volatile Supplier<GuiItem> border =
            () -> ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(BLANK_NAME).asGuiItem();
    private volatile Supplier<GuiItem> filler =
            () -> ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(BLANK_NAME).asGuiItem();
    private volatile Supplier<GuiItem> backBase =
            () -> ItemBuilder.of(Material.ARROW).name(Text.label(message(GuiMessage.BACK))).asGuiItem();
    private volatile Supplier<GuiItem> closeBase =
            () -> ItemBuilder.of(Material.BARRIER).name(Text.label(message(GuiMessage.CLOSE))).asGuiItem();
    private volatile Function<PaginatedGui, GuiItem> previousBase =
            gui -> ItemBuilder.of(Material.ARROW).name(Text.label(message(GuiMessage.PREVIOUS))).asGuiItem();
    private volatile Function<PaginatedGui, GuiItem> nextBase =
            gui -> ItemBuilder.of(Material.ARROW).name(Text.label(message(GuiMessage.NEXT))).asGuiItem();
    private volatile Function<PaginatedGui, GuiItem> pageIndicator = gui -> ItemBuilder.of(Material.PAPER)
            .name(Text.label(message(GuiMessage.PAGE, gui.getCurrentPage(), gui.getPagesCount())))
            .amount(Math.min(64, gui.getCurrentPage()))
            .asGuiItem();
    private volatile Supplier<GuiItem> loading =
            () -> ItemBuilder.of(Material.CLOCK).name(Text.label(message(GuiMessage.LOADING))).asGuiItem();
    private volatile Supplier<GuiItem> loadFailed =
            () -> ItemBuilder.of(Material.BARRIER).name(Text.label(message(GuiMessage.LOAD_FAILED))).asGuiItem();
    private volatile @Nullable ThemeSound clickSound = new ThemeSound(Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    private volatile @Nullable ThemeSound successSound = new ThemeSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    private volatile @Nullable ThemeSound denySound = new ThemeSound(Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    private volatile @Nullable ThemeSound pageSound = new ThemeSound(Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.0f);

    /** Installs message templates for built-in controls and prompts. Resolve player locales through service themeResolver. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull GuiTheme messages(@NotNull Function<GuiMessage, String> provider) {
        messages = java.util.Objects.requireNonNull(provider, "provider");
        revision.incrementAndGet();
        return this;
    }

    /** Resolves a template and substitutes positional arguments. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull String message(@NotNull GuiMessage key, Object... arguments) {
        String text = java.util.Objects.requireNonNull(messages.apply(key), "message");
        for (int index = 0; index < arguments.length; index++) {
            text = text.replace("{" + index + "}", String.valueOf(arguments[index]));
        }
        return text;
    }

    public @NotNull GuiTheme border(@NotNull Supplier<GuiItem> border) {
        this.border = border;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme filler(@NotNull Supplier<GuiItem> filler) {
        this.filler = filler;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme backButtonItem(@NotNull Supplier<GuiItem> backBase) {
        this.backBase = backBase;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme closeButtonItem(@NotNull Supplier<GuiItem> closeBase) {
        this.closeBase = closeBase;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme previousButtonItem(@NotNull Supplier<GuiItem> previousBase) {
        this.previousBase = gui -> previousBase.get();
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme previousButtonItem(@NotNull Function<PaginatedGui, GuiItem> previousBase) {
        this.previousBase = previousBase;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme nextButtonItem(@NotNull Supplier<GuiItem> nextBase) {
        this.nextBase = gui -> nextBase.get();
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme nextButtonItem(@NotNull Function<PaginatedGui, GuiItem> nextBase) {
        this.nextBase = nextBase;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme pageIndicator(@NotNull Function<PaginatedGui, GuiItem> pageIndicator) {
        this.pageIndicator = pageIndicator;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme loadingItem(@NotNull Supplier<GuiItem> loading) {
        this.loading = loading;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme loadFailedItem(@NotNull Supplier<GuiItem> loadFailed) {
        this.loadFailed = loadFailed;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme clickSound(@Nullable ThemeSound sound) {
        this.clickSound = sound;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme successSound(@Nullable ThemeSound sound) {
        this.successSound = sound;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme denySound(@Nullable ThemeSound sound) {
        this.denySound = sound;
        revision.incrementAndGet();
        return this;
    }

    public @NotNull GuiTheme pageSound(@Nullable ThemeSound sound) {
        this.pageSound = sound;
        revision.incrementAndGet();
        return this;
    }

    long revision() {
        return revision.get();
    }

    public @NotNull GuiItem border() {
        return border.get();
    }

    public @NotNull GuiItem filler() {
        return filler.get();
    }

    public @NotNull GuiItem loading() {
        return loading.get();
    }

    public @NotNull GuiItem loadFailed() {
        return loadFailed.get();
    }

    public void applyBorder(@NotNull BaseGui gui) {
        gui.filler().fillBorder(border());
    }

    public void applyFiller(@NotNull BaseGui gui) {
        gui.filler().fill(filler());
    }

    public @NotNull GuiItem backButton() {
        GuiItem item = backBase.get();
        item.setAction(event -> {
            click(event.getWhoClicked());
            if (event.getInventory().getHolder() instanceof BaseGui current) {
                current.service().navigation().back(event.getWhoClicked());
            } else {
                GuiNavigator.back(event.getWhoClicked());
            }
        });
        return item;
    }

    public @NotNull GuiItem backButton(@NotNull Runnable back) {
        GuiItem item = backBase.get();
        item.setAction(event -> {
            click(event.getWhoClicked());
            back.run();
        });
        return item;
    }

    public @NotNull GuiItem closeButton(@NotNull BaseGui gui) {
        GuiItem item = closeBase.get();
        item.setAction(event -> {
            click(event.getWhoClicked());
            gui.service().navigation().clear(event.getWhoClicked());
            gui.close(event.getWhoClicked());
        });
        return item;
    }

    public @NotNull GuiItem previousButton(@NotNull PaginatedGui gui) {
        GuiItem item = previousBase.apply(gui);
        item.setAction(event -> {
            if (gui.previous()) {
                page(event.getWhoClicked());
            }
        });
        return item;
    }

    public @NotNull GuiItem nextButton(@NotNull PaginatedGui gui) {
        GuiItem item = nextBase.apply(gui);
        item.setAction(event -> {
            if (gui.next()) {
                page(event.getWhoClicked());
            }
        });
        return item;
    }

    public @NotNull GuiItem pageIndicator(@NotNull PaginatedGui gui) {
        return pageIndicator.apply(gui);
    }

    public void click(@NotNull HumanEntity viewer) {
        play(clickSound, viewer);
    }

    public void success(@NotNull HumanEntity viewer) {
        play(successSound, viewer);
    }

    public void deny(@NotNull HumanEntity viewer) {
        play(denySound, viewer);
    }

    public void page(@NotNull HumanEntity viewer) {
        play(pageSound, viewer);
    }

    private static void play(@Nullable ThemeSound sound, @NotNull HumanEntity viewer) {
        if (sound != null) {
            sound.play(viewer);
        }
    }
}
