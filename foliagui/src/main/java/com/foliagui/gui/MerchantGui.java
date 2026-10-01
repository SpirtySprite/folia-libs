package com.foliagui.gui;

import com.foliagui.internal.InventoryViews;
import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class MerchantGui {

    private static final int RESULT_SLOT = 2;

    private final FoliaGUIService service;
    private final Component title;
    private final List<MerchantRecipe> recipes;
    private final BiConsumer<Player, MerchantRecipe> onTrade;
    private final Consumer<Player> onClose;
    private final BiConsumer<Player, MerchantRecipe> onPurchase;
    private volatile InventoryIdentity opened;

    private MerchantGui(Builder builder) {
        this.service = builder.service;
        this.title = builder.title;
        this.recipes = List.copyOf(builder.recipes);
        this.onPurchase = builder.onPurchase;
        this.onTrade = builder.onTrade;
        this.onClose = builder.onClose;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static boolean hasSession(@NotNull HumanEntity player) {
        return FoliaGUI.isInitialised() && hasSession(FoliaGUI.service(), player);
    }

    public static boolean hasSession(@NotNull FoliaGUIService service, @NotNull HumanEntity player) {
        return service.sessions().merchant.has(player);
    }

    private @NotNull FoliaGUIService service() {
        return service != null ? service : FoliaGUI.service();
    }

    public void open(@NotNull Player player) {
        FoliaGUIService owner = service();
        owner.scheduler().runForEntity(player, () -> {
            Merchant merchant = Bukkit.createMerchant(title);
            merchant.setRecipes(recipes);
            org.bukkit.inventory.InventoryView view = player.openMerchant(merchant, true);
            if (view != null) {
                opened = new InventoryIdentity(InventoryViews.top(view));
                owner.sessions().merchant.put(player, this);
            }
        }, null);
    }

    @ApiStatus.Internal
    public static boolean handleClick(@NotNull FoliaGUIService service, @NotNull InventoryClickEvent event) {
        MerchantGui gui = service.sessions().merchant.get(event.getWhoClicked());
        if (gui == null || gui.opened == null || gui.opened.inventory() != event.getInventory()
                || !(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return false;
        }
        if (gui.onTrade != null && event.getSlot() == RESULT_SLOT
                && merchantInventory.equals(event.getClickedInventory())) {
            MerchantRecipe recipe = merchantInventory.getSelectedRecipe();
            if (recipe != null) {
                Player player = (Player) event.getWhoClicked();
                service.scheduler().runForEntity(player, () -> gui.onTrade.accept(player, recipe), null);
            }
        }
        return true;
    }

    public static void clearSessions() {
        if (FoliaGUI.isInitialised()) {
            FoliaGUI.service().sessions().merchant.clear();
        }
    }

    @ApiStatus.Internal
    public static boolean handleClose(@NotNull FoliaGUIService service, @NotNull InventoryCloseEvent event) {
        MerchantGui gui = service.sessions().merchant.get(event.getPlayer());
        if (gui == null || gui.opened == null || gui.opened.inventory() != event.getInventory()
                || !service.sessions().merchant.remove(event.getPlayer(), gui)) {
            return false;
        }
        if (gui.onClose != null) {
            gui.onClose.accept((Player) event.getPlayer());
        }
        return true;
    }

    private record InventoryIdentity(org.bukkit.inventory.Inventory inventory) { }

    @ApiStatus.Internal
    public static void handlePurchase(FoliaGUIService service, io.papermc.paper.event.player.PlayerPurchaseEvent event) {
        MerchantGui gui = service.sessions().merchant.get(event.getPlayer());
        if (!event.isCancelled() && gui != null && gui.onPurchase != null && gui.opened != null
                && InventoryViews.top(event.getPlayer().getOpenInventory()) == gui.opened.inventory()) {
            gui.onPurchase.accept(event.getPlayer(), event.getTrade());
        }
    }

    public static final class Builder {
        private FoliaGUIService service;

        public @NotNull Builder service(@NotNull FoliaGUIService service) {
            this.service = service;
            return this;
        }

        private Component title = Component.empty();
        private final List<MerchantRecipe> recipes = new ArrayList<>();
        private BiConsumer<Player, MerchantRecipe> onTrade;
        private BiConsumer<Player, MerchantRecipe> onPurchase;
        private Consumer<Player> onClose;

        public @NotNull Builder title(@NotNull String title) {
            this.title = Text.of(title);
            return this;
        }

        public @NotNull Builder title(@NotNull Component title) {
            this.title = title;
            return this;
        }

        public @NotNull Builder addRecipe(@NotNull MerchantRecipe recipe) {
            recipes.add(recipe);
            return this;
        }

        public @NotNull Builder addRecipe(@NotNull ItemStack result, @NotNull List<ItemStack> ingredients) {
            MerchantRecipe recipe = new MerchantRecipe(result, Integer.MAX_VALUE);
            recipe.setIngredients(ingredients);
            recipes.add(recipe);
            return this;
        }

        /**
         * Receives a result-slot click, which does not prove a purchase succeeded.
         * @deprecated Use {@link #onResultClick(BiConsumer)} for clicks or {@link #onPurchase(BiConsumer)} for accepted purchases.
         */
        @Deprecated
        public @NotNull Builder onTrade(@NotNull BiConsumer<Player, MerchantRecipe> onTrade) {
            this.onTrade = onTrade;
            return this;
        }

        /** Receives result-slot clicks on the owning thread, including clicks that do not produce a trade. */
        @ApiStatus.Experimental
        public @NotNull Builder onResultClick(@NotNull BiConsumer<Player, MerchantRecipe> callback) {
            onTrade = java.util.Objects.requireNonNull(callback, "callback");
            return this;
        }

        /** Receives noncancelled Paper purchase events at MONITOR. This is server acceptance, not a persistence receipt. */
        @ApiStatus.Experimental
        public @NotNull Builder onPurchase(@NotNull BiConsumer<Player, MerchantRecipe> callback) {
            onPurchase = java.util.Objects.requireNonNull(callback, "callback");
            return this;
        }

        public @NotNull Builder onClose(@NotNull Consumer<Player> onClose) {
            this.onClose = onClose;
            return this;
        }

        public @NotNull MerchantGui build() {
            if (recipes.isEmpty()) {
                throw new IllegalStateException("MerchantGui needs at least one recipe; call addRecipe(...) first");
            }
            return new MerchantGui(this);
        }

        public void open(@NotNull Player player) {
            build().open(player);
        }
    }
}
