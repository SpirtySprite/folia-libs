package io.github.spirtysprite.integration;

import com.foliagui.internal.InventoryViews;
import com.foliagui.FoliaGUIService;
import com.foliagui.gui.AnvilGui;
import com.foliagui.gui.AsyncContent;
import com.foliagui.gui.Gui;
import com.foliagui.gui.GuiOperationResult;
import com.foliagui.gui.InputResult;
import com.foliagui.gui.MerchantGui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.gui.RemotePages;
import com.foliagui.gui.SignGui;
import com.foliagui.gui.StorageGui;
import com.foliagui.gui.TextInput;
import com.foliagui.item.GuiItem;
import net.foliacommons.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class GuiScenario {
    private final FoliaGUIService service;
    private final Player player;
    private final Scheduler scheduler;

    private GuiScenario(FoliaGUIService service, Player player, Scheduler scheduler) {
        this.service = service;
        this.player = player;
        this.scheduler = scheduler;
    }

    static CompletableFuture<String> run(FoliaGUIService service, Player player, Scheduler scheduler) {
        GuiScenario checks = new GuiScenario(service, player, scheduler);
        return checks.storage().thenCompose(v -> checks.content()).thenCompose(v -> checks.remote())
                .thenCompose(v -> checks.input()).thenCompose(v -> checks.anvil())
                .thenCompose(v -> checks.merchant()).thenCompose(v -> checks.sign())
                .thenApply(v -> "GUI owner scheduling, storage, stale content, remote pages, input fallback and native session identity passed");
    }

    private CompletableFuture<Void> storage() {
        return entity(() -> {
            StorageGui storage = new StorageGui(3, Component.text("Storage validation"));
            storage.service(service);
            storage.setItem(0, new GuiItem(Material.BARRIER));
            return storage;
        }).thenCompose(storage -> {
            var opened = new CompletableFuture<GuiOperationResult>();
            scheduler.runAsync(() -> storage.openAsync(player).whenComplete((value, failure) -> {
                if (failure == null) {
                    opened.complete(value);
                } else {
                    opened.completeExceptionally(failure);
                }
            }));
            return opened.thenCompose(outcome -> entity(() -> {
                require(outcome == GuiOperationResult.OPENED, "async storage open rejected");
                storage.setStorageContents(new ItemStack[]{null, new ItemStack(Material.DIAMOND, 3)});
                storage.updateTitle("Retitled storage");
                storage.update();
                require(storage.getInventory().getItem(1).getAmount() == 3, "storage title lost deposits");
                require(storage.getInventory().getItem(0).getType() == Material.BARRIER, "storage control lost");
                return storage;
            })).thenCompose(value -> value.storageContentsAsync()).thenCompose(contents -> entity(() -> {
                require(contents[0] == null && contents[1].getAmount() == 3, "storage snapshot is invalid");
                contents[1].setAmount(1);
                require(storage.getInventory().getItem(1).getAmount() == 3, "storage snapshot aliases deposits");
                return (Void) null;
            })).thenCompose(v -> storage.closeAsync(player)).thenApply(v -> null);
        });
    }

    private CompletableFuture<Void> content() {
        AtomicInteger value = new AtomicInteger();
        return entity(() -> {
            Gui gui = Gui.of(3, "Content validation");
            gui.service(service);
            var first = AsyncContent.loadAsync(gui, player, () -> 1, value::set, null);
            var second = AsyncContent.loadAsync(gui, player, () -> 2, value::set, null);
            return CompletableFuture.allOf(first, second).thenCompose(v -> entity(() -> {
                require(value.get() == 2 && first.join() == GuiOperationResult.SUPERSEDED, "stale content applied");
                require(gui.getGuiItem(13) == null, "loading marker retained");
                return (Void) null;
            })).thenCompose(v -> gui.closeAsync(player)).thenApply(v -> (Void) null);
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> remote() {
        AtomicInteger fetches = new AtomicInteger();
        return entity(() -> {
            PaginatedGui gui = new PaginatedGui(3, Component.text("Remote validation"), 45);
            gui.service(service);
            for (int slot = 18; slot < 27; slot++) {
                gui.setItem(slot, new GuiItem(Material.BARRIER));
            }
            require(gui.pageCapacity() == 18, "effective pagination capacity differs");
            RemotePages<Integer> pages = gui.remotePages(player, request -> {
                fetches.incrementAndGet();
                return CompletableFuture.completedFuture(new RemotePages.Page<>(List.of(request.page()), 36));
            }, amount -> new GuiItem(new ItemStack(Material.STONE, amount)));
            return later(8, () -> {
                require(gui.getPagesCount() == 2 && fetches.get() == 1, "first remote page did not load");
                require(gui.getInventory().getItem(0).getAmount() == 1, "wrong first remote page");
                gui.next();
                return (Void) null;
            }).thenCompose(v -> later(8, () -> {
                require(fetches.get() == 2 && gui.getInventory().getItem(0).getAmount() == 2, "wrong next remote page");
                pages.close();
                return (Void) null;
            })).thenCompose(v -> gui.closeAsync(player)).thenApply(v -> (Void) null);
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> input() {
        return entity(() -> {
            var session = TextInput.builder().mode(TextInput.Mode.AUTO).timeout(40).build().open(service, player);
            return later(8, () -> {
                require(!session.result().isDone(), "automatic input failed without fallback");
                session.cancel();
                return session.result();
            }).thenCompose(result -> result).thenAccept(result ->
                    require(result.status() == InputResult.Status.CANCELLED, "input cancellation outcome differs"));
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> anvil() {
        if (!present("org.bukkit.inventory.MenuType")) {
            return CompletableFuture.completedFuture(null);
        }
        AtomicInteger completions = new AtomicInteger();
        return entity(() -> {
            AnvilGui input = AnvilGui.builder().service(service).text("typed")
                    .onComplete((p, text) -> {
                        completions.incrementAndGet();
                        return AnvilGui.Response.close();
                    }).build();
            return input.openAsync(player).thenCompose(outcome -> entity(() -> {
                require(outcome == GuiOperationResult.OPENED, "native anvil rejected");
                InventoryView view = player.getOpenInventory();
                var click = new InventoryClickEvent(view, InventoryType.SlotType.RESULT, 2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                require(AnvilGui.handleClick(service, click), "owned anvil click not handled");
                require(completions.get() == 1, "anvil completion count differs");
                return (Void) null;
            }));
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> merchant() {
        AtomicInteger accepted = new AtomicInteger();
        return entity(() -> {
            MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.DIAMOND), 100);
            recipe.setIngredients(List.of(new ItemStack(Material.EMERALD)));
            MerchantGui merchant = MerchantGui.builder().service(service).addRecipe(recipe)
                    .onPurchase((p, trade) -> accepted.incrementAndGet()).build();
            merchant.open(player);
            return later(3, () -> {
                InventoryView view = player.getOpenInventory();
                require(InventoryViews.top(view) instanceof MerchantInventory, "merchant did not open");
                try {
                    var type = io.papermc.paper.event.player.PlayerPurchaseEvent.class;
                    io.papermc.paper.event.player.PlayerPurchaseEvent event;
                    if (presentMerchantConstructor(type)) {
                        event = type.getConstructor(Player.class, org.bukkit.inventory.Merchant.class, MerchantRecipe.class, boolean.class, boolean.class)
                                .newInstance(player, ((MerchantInventory) InventoryViews.top(view)).getMerchant(), recipe, false, true);
                    } else {
                        event = type.getConstructor(Player.class, MerchantRecipe.class, boolean.class, boolean.class)
                                .newInstance(player, recipe, false, true);
                    }
                    event.setCancelled(true);
                    MerchantGui.handlePurchase(service, event);
                    require(accepted.get() == 0, "cancelled purchase callback fired");
                    event.setCancelled(false);
                    MerchantGui.handlePurchase(service, event);
                    require(accepted.get() == 1, "accepted purchase callback missing");
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot construct purchase fixture", failure);
                }
                Gui unrelated = Gui.of(1, "Unrelated window");
                unrelated.service(service);
                return unrelated.openAsync(player).thenCompose(outcome -> entity(() -> {
                    require(!MerchantGui.handleClose(service, new InventoryCloseEvent(view)), "stale merchant close consumed");
                    return (Void) null;
                })).thenCompose(v -> unrelated.closeAsync(player)).thenApply(v -> (Void) null);
            }).thenCompose(stage -> stage);
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> sign() {
        if (!present("io.papermc.paper.event.packet.UncheckedSignChangeEvent")) {
            return CompletableFuture.completedFuture(null);
        }
        AtomicInteger submitted = new AtomicInteger();
        return entity(() -> {
            SignGui sign = SignGui.builder().service(service).position(p -> p.getLocation().add(64, -3, 0))
                    .onComplete((p, lines) -> submitted.incrementAndGet()).build();
            return sign.openAsync(player).thenCompose(outcome -> entity(() -> {
                require(outcome == GuiOperationResult.OPENED, "sign did not open");
                require(SignGui.handleSignChange(service, player, List.of(Component.text("entered"), Component.empty(), Component.empty(), Component.empty())),
                        "sign completion missing");
                require(submitted.get() == 1, "sign completion count differs");
                return (Void) null;
            }));
        }).thenCompose(stage -> stage);
    }

    private <T> CompletableFuture<T> entity(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        if (!scheduler.runForEntity(player, () -> {
            try {
                require(Bukkit.getServer().isOwnedByCurrentRegion(player), "player context is not owned");
                result.complete(action.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, () -> result.completeExceptionally(new IllegalStateException("player retired")))) {
            result.completeExceptionally(new IllegalStateException("player dispatch rejected"));
        }
        return result;
    }

    private <T> CompletableFuture<T> later(long ticks, Supplier<T> action) {
        var result = new CompletableFuture<T>();
        scheduler.runForEntityLater(player, () -> {
            try {
                result.complete(action.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, () -> result.completeExceptionally(new IllegalStateException("player retired")), ticks);
        return result;
    }

    private static boolean presentMerchantConstructor(Class<?> type) {
        try {
            type.getConstructor(Player.class, org.bukkit.inventory.Merchant.class, MerchantRecipe.class, boolean.class, boolean.class);
            return true;
        } catch (NoSuchMethodException missing) {
            return false;
        }
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, GuiScenario.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
