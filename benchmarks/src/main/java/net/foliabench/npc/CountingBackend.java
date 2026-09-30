package net.foliabench.npc;

import net.folianpc.api.ClickType;
import net.folianpc.internal.protocol.NpcSnapshot;
import net.folianpc.internal.protocol.ProtocolBackend;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** A protocol backend that only counts what it is asked to send. */
final class CountingBackend implements ProtocolBackend {
    private final AtomicInteger ids = new AtomicInteger(1);
    long shows;
    long hides;
    long looks;
    long moves;

    @Override
    public int nextEntityId() {
        return ids.getAndIncrement();
    }

    @Override
    public void show(Player viewer, NpcSnapshot npc) {
        shows++;
    }

    @Override
    public void hide(Player viewer, NpcSnapshot npc) {
        hides++;
    }

    @Override
    public void removeEntities(Player viewer, int[] entityIds) {
    }

    @Override
    public void updateMeta(Player viewer, NpcSnapshot npc) {
    }

    @Override
    public void refreshHologram(Player viewer, NpcSnapshot npc) {
    }

    @Override
    public void animate(Player viewer, int entityId, int action) {
    }

    @Override
    public void scale(Player viewer, int entityId, double scale) {
    }

    @Override
    public void look(Player viewer, int entityId, float yaw, float pitch) {
        looks++;
    }

    @Override
    public void move(Player viewer, int entityId, double dx, double dy, double dz) {
        moves++;
    }

    @Override
    public void equip(Player viewer, int entityId, Map<EquipmentSlot, ItemStack> equipment) {
    }

    @Override
    public void injectViewer(Player viewer) {
    }

    @Override
    public void ejectViewer(Player viewer) {
    }

    @Override
    public void onInteract(InteractSink sink) {
    }
}
