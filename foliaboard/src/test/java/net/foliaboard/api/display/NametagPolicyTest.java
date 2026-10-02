package net.foliaboard.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NametagPolicyTest {
    @Test
    void layoutsCopyCollectionsAndItemsAndRejectAmbiguousKeys() {
        var item = item(2);
        var badge = new NametagLayout.Item("badge", item, 0.5, DisplayStyle.defaults(),
                ItemDisplay.ItemDisplayTransform.FIXED, 0, 48);
        item.setAmount(7);
        badge.item().setAmount(9);
        assertEquals(2, badge.item().getAmount());
        var builder = NametagLayout.builder().element(badge);
        var built = builder.build();
        builder.text("name", Component.text("Name"), 0.25);
        assertEquals(1, built.elements().size());
        assertThrows(UnsupportedOperationException.class, () -> built.elements().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.element(badge).build());
        assertThrows(IllegalArgumentException.class, () -> NametagLayout.builder()
                .text(" ", Component.empty(), 0).build());
        assertThrows(IllegalArgumentException.class, () -> new NametagLayout.Text("invalid", Component.empty(),
                Double.NaN, DisplayStyle.defaults(), TextDisplayStyle.defaults(), 0, 48));
        assertThrows(IllegalArgumentException.class, () -> new NametagLayout.Text("invalid", Component.empty(),
                0, DisplayStyle.defaults(), TextDisplayStyle.defaults(), 48, 48));
    }

    private ItemStack item(int amount) {
        var value = new AtomicInteger(amount);
        var item = mock(ItemStack.class);
        when(item.getAmount()).thenAnswer(call -> value.get());
        when(item.clone()).thenAnswer(call -> item(value.get()));
        doAnswer(call -> { value.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        return item;
    }

    @Test
    void policyCopiesPreserveSettingsAndRejectInvalidPeriodsOrDistances() {
        var profile = NametagProfile.builder().refresh(new NametagRefresh(5, 7))
                .transition(new NametagTransition(3, 4))
                .distance(new NametagDistance(16, 48, 1, 0.5f)).build();
        var changed = profile.toBuilder().layout(NametagLayout.builder()
                .text("name", Component.text("Name"), 0.25).build()).build();
        assertEquals(profile.refresh(), changed.refresh());
        assertEquals(profile.transition(), changed.transition());
        assertEquals(profile.distance(), changed.distance());
        assertEquals(0.5, profile.distance().opacity(32));
        assertEquals(0.75f, profile.distance().scale(32));
        assertThrows(IllegalArgumentException.class, () -> new NametagRefresh(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new NametagTransition(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new NametagDistance(48, 16, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NametagDistance(0, 16, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> profile.distance().opacity(Double.NaN));
        assertThrows(NullPointerException.class, () -> profile.toBuilder().visibility(null).build());
        assertThrows(IllegalArgumentException.class, () -> new NametagStatus(NametagStatus.Reason.EMPTY, -1));
        assertThrows(NullPointerException.class, () -> new NametagStatus(null, 0));
    }
}
