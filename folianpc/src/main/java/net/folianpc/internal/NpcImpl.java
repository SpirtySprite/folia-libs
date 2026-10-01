package net.folianpc.internal;

import net.folianpc.api.ClickType;
import net.folianpc.api.MovementResult;
import net.folianpc.api.MovementTask;
import net.folianpc.api.NavigationOptions;
import net.folianpc.api.Emote;
import net.folianpc.api.Npc;
import net.folianpc.api.NpcAction;
import net.folianpc.api.NpcClickListener;
import net.folianpc.api.NpcAppearance;
import net.folianpc.api.NpcData;
import net.folianpc.api.MetadataType;
import net.folianpc.api.MobVariant;
import net.folianpc.api.NametagStyle;
import net.folianpc.api.NpcPose;
import net.folianpc.api.Skin;
import org.bukkit.entity.Player;
import net.folianpc.internal.geometry.LookAt;
import net.folianpc.internal.protocol.HologramLine;
import net.folianpc.internal.protocol.NpcSnapshot;
import net.folianpc.internal.protocol.RawMeta;
import org.bukkit.Location;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NpcImpl implements Npc {

    private static final double HOLOGRAM_BASE = 2.05;
    private static final double HOLOGRAM_SPACING = 0.28;
    private static final int MAX_PROFILE_NAME = 16;

    public record ActionEntry(NpcAction action, long delayTicks) {
    }

    private final UUID uuid;
    private final int entityId;
    private volatile EntityType type;
    private final NpcManager manager;
    private volatile UUID owner;

    private volatile Position position;

    private long movementGeneration;
    private MovementRequest movement;
    private volatile double[] walkTarget;
    private volatile double walkSpeed;
    private final Queue<double[]> waypoints = new ConcurrentLinkedQueue<>();

    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Map<EquipmentSlot, ItemStack> equipment = new ConcurrentHashMap<>();
    private final Map<ClickType, List<ActionEntry>> actions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> visibility = new ConcurrentHashMap<>();
    private volatile java.util.function.Predicate<org.bukkit.entity.Player> visibleWhen;
    private final Map<UUID, Integer> lastLook = new ConcurrentHashMap<>();

    private volatile String name;
    private volatile Skin skin;
    private volatile boolean mirrorSkin;
    private volatile NpcClickListener clickListener;
    private volatile boolean lookAtPlayers;
    private volatile boolean removed;
    private volatile long cooldownMillis;

    private volatile List<String> nametag = List.of();
    private volatile int[] nametagIds = new int[0];
    private volatile boolean nametagVisible = true;
    private volatile NametagStyle nametagStyle = NametagStyle.defaults();
    private volatile boolean glowing;
    private volatile boolean invisible;
    private volatile boolean skinLayers = true;
    private volatile double scale = 1.0;
    private volatile NamedTextColor glowColor;
    private volatile boolean collidable = true;
    private volatile boolean showInTabList;

    private volatile NpcPose pose = NpcPose.STANDING;
    private volatile boolean baby;
    private volatile MobVariant mobVariant = MobVariant.defaults();
    private final Map<Integer, RawMeta> rawMeta = new ConcurrentHashMap<>();

    private volatile int nametagRefreshPasses;
    private int nametagRefreshCountdown;

    private volatile double viewDistance;
    private volatile double visibilityHysteresis;

    private volatile double proximityRadius;
    private volatile BiConsumer<Npc, Player> nearCallback;
    private volatile BiConsumer<Npc, Player> leaveCallback;
    private final Set<UUID> nearby = ConcurrentHashMap.newKeySet();

    NpcImpl(UUID uuid, int entityId, String name, EntityType type, Position position, NpcManager manager) {
        this.uuid = uuid;
        this.entityId = entityId;
        this.name = name;
        this.type = type;
        this.position = position;
        this.manager = manager;
    }

    @Override
    public synchronized Npc teleport(Location target) {
        ensureLive();
        return teleportTo(toPosition(target));
    }

    @Override
    public synchronized Npc walkTo(Location target, double blocksPerSecond) {
        ensureLive();
        String world = target.getWorld() != null ? target.getWorld().getName() : "world";
        if (!world.equals(position.world())) {
            return teleportTo(toPosition(target));
        }
        return walkToward(target.getX(), target.getY(), target.getZ(), blocksPerSecond);
    }

    @Override
    public CompletableFuture<Boolean> navigateTo(Location target, double blocksPerSecond) {
        return manager.navigate(this, target, blocksPerSecond);
    }

    synchronized Npc teleportTo(Position target) {
        endMovement(MovementResult.Status.SUPERSEDED);
        this.position = target;
        if (!removed) {
            manager.reposition(this);
        }
        return this;
    }

    synchronized Npc walkToward(double x, double y, double z, double blocksPerSecond) {
        finite(blocksPerSecond, "movement speed");
        new Position(position.world(), x, y, z, 0, 0);
        endMovement(MovementResult.Status.SUPERSEDED);
        this.walkSpeed = Math.max(0.05, blocksPerSecond);
        this.walkTarget = new double[]{x, y, z};
        return this;
    }

    @Override
    public MovementTask navigateTo(Location target, double speed, NavigationOptions options) {
        return manager.navigate(this, target, speed, options);
    }

    synchronized MovementRequest beginMovement() {
        endMovement(MovementResult.Status.SUPERSEDED);
        MovementRequest request = new MovementRequest(movementGeneration, this);
        if (removed || manager.closed()) {
            request.finish(MovementResult.of(manager.closed() ? MovementResult.Status.SHUTDOWN : MovementResult.Status.REMOVED));
        } else {
            movement = request;
        }
        return request;
    }

    synchronized boolean installRoute(MovementRequest request, List<double[]> route, double speed) {
        if (movement != request || removed || manager.closed()) {
            return false;
        }
        waypoints.clear();
        for (double[] point : route) {
            waypoints.add(point.clone());
        }
        walkSpeed = Math.max(0.05, speed);
        walkTarget = waypoints.poll();
        request.ready();
        return true;
    }

    synchronized void cancelMovement(MovementRequest request, MovementResult.Status status) {
        completeMovement(request, MovementResult.of(status));
    }

    synchronized void completeMovement(MovementRequest request, MovementResult outcome) {
        if (movement == request) {
            movement = null;
            walkTarget = null;
            waypoints.clear();
            request.finish(outcome);
        }
    }

    synchronized long movementGeneration() {
        return movementGeneration;
    }

    synchronized double[] dimensions(NavigationOptions options) {
        double width = switch (type) {
            case SLIME, MAGMA_CUBE -> 2.1;
            case RAVAGER -> 1.95;
            case IRON_GOLEM -> 1.4;
            case HORSE, DONKEY, MULE, CAMEL -> 1.7;
            case SPIDER -> 1.4;
            default -> 0.6;
        };
        double height = switch (type) {
            case ENDERMAN -> 2.9;
            case IRON_GOLEM -> 2.7;
            case WARDEN -> 2.9;
            case CAMEL -> 2.4;
            case CHICKEN, RABBIT -> 0.7;
            case SPIDER -> 0.9;
            case SLIME, MAGMA_CUBE -> 2.1;
            default -> 1.8;
        };
        if (pose == NpcPose.SLEEPING || pose == NpcPose.SWIMMING) {
            height = 0.6;
        } else if (pose == NpcPose.CROUCHING) {
            height *= 0.85;
        }
        double factor = scale * (baby ? 0.5 : 1.0);
        return new double[]{options.width() > 0 ? options.width() : width * factor,
                options.height() > 0 ? options.height() : height * factor};
    }

    private void endMovement(MovementResult.Status status) {
        movementGeneration++;
        MovementRequest ending = movement;
        movement = null;
        walkTarget = null;
        waypoints.clear();
        if (ending != null) {
            ending.finish(MovementResult.of(status));
        }
    }

    private void arrived() {
        if (walkTarget == null && movement != null) {
            MovementRequest ending = movement;
            movement = null;
            ending.finish(MovementResult.of(MovementResult.Status.ARRIVED));
        }
    }

    @Override
    public synchronized Npc stopWalking() {
        endMovement(MovementResult.Status.CANCELLED);
        return this;
    }

    @Override
    public boolean moving() {
        return walkTarget != null;
    }

    synchronized double[] stepWalk(double seconds) {
        double[] target = walkTarget;
        if (target == null) {
            return null;
        }
        Position from = position;
        double dx = target[0] - from.x();
        double dy = target[1] - from.y();
        double dz = target[2] - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-4) {
            walkTarget = waypoints.poll();
            arrived();
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double step = walkSpeed * seconds;
        if (distance <= step) {
            this.position = new Position(from.world(), target[0], target[1], target[2], yaw, from.pitch());
            walkTarget = waypoints.poll();
            arrived();
            return new double[]{dx, dy, dz};
        }
        double f = step / distance;
        this.position = new Position(from.world(),
                from.x() + dx * f, from.y() + dy * f, from.z() + dz * f, yaw, from.pitch());
        return new double[]{dx * f, dy * f, dz * f};
    }

    private static Position toPosition(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return new Position(world, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    @Override
    public synchronized Npc visibilityHysteresis(double blocks) {
        ensureLive();
        finite(blocks, "visibility hysteresis");
        if (blocks < 0) {
            throw new IllegalArgumentException("Visibility hysteresis must be nonnegative");
        }
        visibilityHysteresis = blocks;
        return this;
    }

    @Override
    public double visibilityHysteresis() { return visibilityHysteresis; }

    @Override
    public UUID id() {
        return uuid;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public synchronized Npc name(String name) {
        ensureLive();
        this.name = name == null || name.isBlank() ? "NPC" : name;
        refresh(nametagIds);
        return this;
    }

    @Override
    public synchronized Npc viewDistance(double blocks) {
        ensureLive();
        finite(blocks, "view distance");
        this.viewDistance = blocks;
        return this;
    }

    @Override
    public double viewDistance() {
        return viewDistance;
    }

    @Override
    public EntityType type() {
        return type;
    }

    @Override
    public synchronized Npc type(EntityType type) {
        ensureLive();
        this.type = type != null ? type : EntityType.PLAYER;
        refresh(nametagIds);
        return this;
    }

    @Override
    public String world() {
        return position.world();
    }

    @Override
    public double x() {
        return position.x();
    }

    @Override
    public double y() {
        return position.y();
    }

    @Override
    public double z() {
        return position.z();
    }

    public int entityId() {
        return entityId;
    }

    public Position position() {
        return position;
    }

    Set<UUID> viewers() {
        return viewers;
    }

    NpcClickListener clickListener() {
        return clickListener;
    }

    @Override
    public synchronized Npc lookAtPlayers(boolean enabled) {
        ensureLive();
        this.lookAtPlayers = enabled;
        return this;
    }

    @Override
    public boolean lookAtPlayers() {
        return lookAtPlayers;
    }

    @Override
    public synchronized Npc onClick(NpcClickListener listener) {
        ensureLive();
        this.clickListener = listener;
        return this;
    }

    @Override
    public synchronized Npc skin(Skin skin) {
        ensureLive();
        this.skin = skin;
        refresh(nametagIds);
        return this;
    }

    @Override
    public Skin skin() {
        return skin;
    }

    @Override
    public synchronized Npc mirrorSkin(boolean enabled) {
        ensureLive();
        this.mirrorSkin = enabled;
        refresh(nametagIds);
        return this;
    }

    @Override
    public boolean mirrorSkin() {
        return mirrorSkin;
    }

    @Override
    public synchronized Npc equipment(EquipmentSlot slot, ItemStack item) {
        ensureLive();
        if (item == null || item.getType().isAir()) {
            equipment.remove(slot);
        } else {
            equipment.put(slot, item.clone());
        }
        if (!removed) {
            manager.updateEquipment(this);
        }
        return this;
    }

    @Override
    public synchronized Map<EquipmentSlot, ItemStack> equipment() {
        return ItemCopies.copy(equipment);
    }

    @Override
    public synchronized Npc nametag(List<String> lines) {
        ensureLive();
        List<String> clean = lines == null ? List.of() : List.copyOf(lines);
        int[] stale = nametagIds;
        if (clean.size() != nametagIds.length) {
            int[] ids = new int[clean.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = manager.newEntityId();
            }
            this.nametagIds = ids;
        }
        this.nametag = clean;
        this.nametagVisible = clean.isEmpty();
        refresh(stale);
        return this;
    }

    @Override
    public List<String> nametag() {
        return nametag;
    }

    @Override
    public synchronized Npc nametagVisible(boolean visible) {
        ensureLive();
        this.nametagVisible = visible;
        refresh(nametagIds);
        return this;
    }

    @Override
    public boolean nametagVisible() {
        return nametagVisible;
    }

    @Override
    public synchronized Npc nametagStyle(NametagStyle style) {
        ensureLive();
        NametagStyle next = style == null ? NametagStyle.defaults() : style;
        if (next.equals(nametagStyle)) {
            return this;
        }
        this.nametagStyle = next;
        if (!removed && !nametag.isEmpty()) {
            manager.updateNametag(this);
        }
        return this;
    }

    @Override
    public NametagStyle nametagStyle() {
        return nametagStyle;
    }

    @Override
    public synchronized Npc glowing(boolean value) {
        ensureLive();
        this.glowing = value;
        return pushMeta();
    }

    @Override
    public boolean glowing() {
        return glowing;
    }

    @Override
    public synchronized Npc invisible(boolean value) {
        ensureLive();
        this.invisible = value;
        return pushMeta();
    }

    @Override
    public boolean invisible() {
        return invisible;
    }

    @Override
    public synchronized Npc skinLayers(boolean value) {
        ensureLive();
        this.skinLayers = value;
        return pushMeta();
    }

    @Override
    public boolean skinLayers() {
        return skinLayers;
    }

    @Override
    public synchronized Npc scale(double value) {
        ensureLive();
        finite(value, "scale");
        this.scale = Math.max(0.0625, value);
        if (!removed) {
            manager.updateScale(this);
        }
        return this;
    }

    @Override
    public double scale() {
        return scale;
    }

    @Override
    public synchronized Npc metadata(int index, MetadataType type, Object value) {
        ensureLive();
        if (value == null) {
            rawMeta.remove(index);
        } else {
            rawMeta.put(index, new RawMeta(type, value));
        }
        return pushMeta();
    }

    @Override
    public synchronized Npc pose(NpcPose pose) {
        ensureLive();
        this.pose = pose == null ? NpcPose.STANDING : pose;
        return pushMeta();
    }

    @Override
    public NpcPose pose() {
        return pose;
    }

    @Override
    public synchronized Npc baby(boolean value) {
        ensureLive();
        this.baby = value;
        return pushMeta();
    }

    @Override
    public boolean baby() {
        return baby;
    }

    @Override
    public synchronized Npc variant(int value) {
        ensureLive();
        this.mobVariant = new MobVariant(value, mobVariant.variantName(), mobVariant.villagerProfession(),
                mobVariant.villagerType(), mobVariant.villagerLevel());
        return pushMeta();
    }

    @Override
    public int variant() {
        return mobVariant.variant();
    }

    @Override
    public synchronized Npc variant(String name) {
        ensureLive();
        this.mobVariant = new MobVariant(mobVariant.variant(), name, mobVariant.villagerProfession(),
                mobVariant.villagerType(), mobVariant.villagerLevel());
        return pushMeta();
    }

    @Override
    public String variantName() {
        return mobVariant.variantName();
    }

    @Override
    public synchronized Npc villagerProfession(String profession) {
        ensureLive();
        this.mobVariant = new MobVariant(mobVariant.variant(), mobVariant.variantName(), profession,
                mobVariant.villagerType(), mobVariant.villagerLevel());
        return pushMeta();
    }

    @Override
    public String villagerProfession() {
        return mobVariant.villagerProfession();
    }

    @Override
    public synchronized Npc villagerType(String biomeType) {
        ensureLive();
        this.mobVariant = new MobVariant(mobVariant.variant(), mobVariant.variantName(),
                mobVariant.villagerProfession(), biomeType, mobVariant.villagerLevel());
        return pushMeta();
    }

    @Override
    public String villagerType() {
        return mobVariant.villagerType();
    }

    @Override
    public synchronized Npc villagerLevel(int level) {
        ensureLive();
        this.mobVariant = new MobVariant(mobVariant.variant(), mobVariant.variantName(),
                mobVariant.villagerProfession(), mobVariant.villagerType(), level);
        return pushMeta();
    }

    @Override
    public int villagerLevel() {
        return mobVariant.villagerLevel();
    }

    @Override
    public MobVariant mobVariant() {
        return mobVariant;
    }

    @Override
    public synchronized Npc mobVariant(MobVariant variant) {
        ensureLive();
        this.mobVariant = variant == null ? MobVariant.defaults() : variant;
        return pushMeta();
    }

    @Override
    public synchronized Npc swing() {
        ensureLive();
        if (!removed) {
            manager.animate(this, 0);
        }
        return this;
    }

    @Override
    public synchronized Npc swingOffHand() {
        ensureLive();
        if (!removed) {
            manager.animate(this, 3);
        }
        return this;
    }

    @Override
    public synchronized Npc playEmote(Emote emote) {
        ensureLive();
        if (!removed && emote != null) {
            manager.playEmote(this, emote);
        }
        return this;
    }

    @Override
    public synchronized Npc onPlayerNear(double radius, BiConsumer<Npc, Player> callback) {
        ensureLive();
        finite(radius, "proximity radius");
        this.proximityRadius = Math.max(0.0, radius);
        this.nearCallback = callback;
        return this;
    }

    @Override
    public synchronized Npc onPlayerLeave(BiConsumer<Npc, Player> callback) {
        ensureLive();
        this.leaveCallback = callback;
        return this;
    }

    boolean hasProximityListeners() {
        return proximityRadius > 0 && (nearCallback != null || leaveCallback != null);
    }

    double proximityRadius() {
        return proximityRadius;
    }

    BiConsumer<Npc, Player> nearCallback() {
        return nearCallback;
    }

    BiConsumer<Npc, Player> leaveCallback() {
        return leaveCallback;
    }

    void syncProximity(Set<UUID> nowNear, Consumer<UUID> onEnter, Consumer<UUID> onLeave) {
        for (UUID id : nowNear) {
            if (nearby.add(id)) {
                onEnter.accept(id);
            }
        }
        nearby.removeIf(id -> {
            if (!nowNear.contains(id)) {
                onLeave.accept(id);
                return true;
            }
            return false;
        });
    }

    @Override
    public synchronized Npc refreshNametag() {
        ensureLive();
        if (!removed) {
            manager.updateNametag(this);
        }
        return this;
    }

    @Override
    public synchronized Npc autoRefreshNametag(long everyTicks) {
        ensureLive();
        this.nametagRefreshPasses = everyTicks <= 0 ? 0 : Math.max(1, (int) (everyTicks / 2));
        this.nametagRefreshCountdown = nametagRefreshPasses;
        return this;
    }

    boolean dueForNametagRefresh() {
        if (nametagRefreshPasses <= 0 || nametag.isEmpty()) {
            return false;
        }
        if (--nametagRefreshCountdown > 0) {
            return false;
        }
        nametagRefreshCountdown = nametagRefreshPasses;
        return true;
    }

    @Override
    public synchronized Npc glowColor(NamedTextColor color) {
        ensureLive();
        this.glowColor = color;
        refresh(nametagIds);
        return this;
    }

    @Override
    public NamedTextColor glowColor() {
        return glowColor;
    }

    @Override
    public synchronized Npc collidable(boolean value) {
        ensureLive();
        this.collidable = value;
        refresh(nametagIds);
        return this;
    }

    @Override
    public boolean collidable() {
        return collidable;
    }

    @Override
    public synchronized Npc showInTabList(boolean value) {
        ensureLive();
        this.showInTabList = value;
        refresh(nametagIds);
        return this;
    }

    @Override
    public boolean showInTabList() {
        return showInTabList;
    }

    @Override
    public synchronized NpcAppearance appearance() {
        return new NpcAppearance(glowing, invisible, skinLayers, scale, glowColor, collidable, nametagVisible);
    }

    @Override
    public synchronized Npc appearance(NpcAppearance appearance) {
        ensureLive();
        this.glowing = appearance.glowing();
        this.invisible = appearance.invisible();
        this.skinLayers = appearance.skinLayers();
        this.scale = appearance.scale();
        this.glowColor = appearance.glowColor();
        this.collidable = appearance.collidable();
        this.nametagVisible = appearance.nametagVisible();
        refresh(nametagIds);
        return this;
    }

    @Override
    public synchronized Npc showTo(UUID playerId) {
        ensureLive();
        visibility.put(playerId, Boolean.TRUE);
        return this;
    }

    @Override
    public synchronized Npc hideFrom(UUID playerId) {
        ensureLive();
        visibility.put(playerId, Boolean.FALSE);
        return this;
    }

    @Override
    public synchronized Npc resetVisibility(UUID playerId) {
        ensureLive();
        visibility.remove(playerId);
        return this;
    }

    @Override
    public synchronized Npc visibleWhen(java.util.function.Predicate<org.bukkit.entity.Player> condition) {
        ensureLive();
        this.visibleWhen = condition;
        return this;
    }

    @Override
    public java.util.function.Predicate<org.bukkit.entity.Player> visibleWhen() {
        return visibleWhen;
    }

    /** Players this NPC is forced visible to, which are shown even when out of range. Empty for most NPCs. */
    boolean hasForcedVisibility() {
        if (visibility.isEmpty()) {
            return false;
        }
        for (Boolean forced : visibility.values()) {
            if (forced) {
                return true;
            }
        }
        return false;
    }

    void forEachForcedVisible(java.util.function.Consumer<UUID> action) {
        for (Map.Entry<UUID, Boolean> entry : visibility.entrySet()) {
            if (entry.getValue()) {
                action.accept(entry.getKey());
            }
        }
    }

    boolean visibleTo(org.bukkit.entity.Player player, boolean inRange) {
        Boolean forced = visibility.get(player.getUniqueId());
        if (forced != null) {
            return forced;
        }
        if (!inRange) {
            return false;
        }
        java.util.function.Predicate<org.bukkit.entity.Player> condition = visibleWhen;
        if (condition == null) {
            return true;
        }
        try {
            return condition.test(player);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    boolean lookChanged(UUID playerId, float yaw, float pitch) {
        int packed = (LookAt.angleByte(yaw) & 0xFF) << 8 | (LookAt.angleByte(pitch) & 0xFF);
        Integer previous = lastLook.put(playerId, packed);
        return previous == null || previous != packed;
    }

    void forgetLook(UUID playerId) {
        lastLook.remove(playerId);
    }

    void forget(UUID playerId) {
        viewers.remove(playerId);
        lastInteract.remove(playerId);
        lastLook.remove(playerId);
        nearby.remove(playerId);
    }

    void dropVisibility(UUID playerId) {
        visibility.remove(playerId);
    }

    @Override
    public synchronized Npc addAction(ClickType type, NpcAction action) {
        ensureLive();
        return addAction(type, action, 0L);
    }

    @Override
    public synchronized Npc addAction(ClickType type, NpcAction action, long delayTicks) {
        ensureLive();
        if (action != null) {
            actions.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                    .add(new ActionEntry(action, Math.max(0L, delayTicks)));
        }
        return this;
    }

    @Override
    public synchronized Npc clearActions(ClickType type) {
        ensureLive();
        actions.remove(type);
        return this;
    }

    public List<ActionEntry> actions(ClickType type) {
        return actions.getOrDefault(type, List.of());
    }

    @Override
    public synchronized Npc cooldown(long millis) {
        ensureLive();
        this.cooldownMillis = Math.max(0L, millis);
        return this;
    }

    @Override
    public long cooldown() {
        return cooldownMillis;
    }

    boolean allowInteract(UUID viewer, long nowMillis) {
        long cooldown = cooldownMillis;
        if (cooldown <= 0) {
            return true;
        }
        boolean[] allowed = {false};
        lastInteract.compute(viewer, (key, last) -> {
            if (last != null && nowMillis - last < cooldown) {
                return last;
            }
            allowed[0] = true;
            return nowMillis;
        });
        return allowed[0];
    }

    boolean needsTeam() {
        return !nametagVisible || glowColor != null || !collidable;
    }

    public String profileName() {
        String wire = needsTeam() ? uuid.toString().replace("-", "") : name;
        return wire.length() > MAX_PROFILE_NAME ? wire.substring(0, MAX_PROFILE_NAME) : wire;
    }

    int[] nametagIds() {
        return nametagIds;
    }

    @Override
    public synchronized void remove() {
        if (!removed) {
            markRemoved();
            manager.unregister(this);
        }
    }

    @Override
    public boolean removed() {
        return removed;
    }

    public synchronized NpcSnapshot snapshot() {
        Skin current = skin;
        Position pos = position;
        return new NpcSnapshot(entityId, uuid, name, profileName(), nametagVisible,
                glowing, invisible, skinLayers, scale,
                glowColor == null ? null : glowColor.toString(), collidable, needsTeam(),
                pose.name(), baby, mobVariant,
                type, pos.world(),
                pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch(),
                current == null ? null : current.value(),
                current == null ? null : current.signature(), mirrorSkin, showInTabList,
                ItemCopies.copy(equipment), hologram(), Map.copyOf(rawMeta));
    }

    @Override
    public synchronized NpcData data() {
        Position pos = position;
        return NpcData.builder()
                .id(uuid).name(name).type(type)
                .position(pos.world(), pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch())
                .lookAtPlayers(lookAtPlayers).skin(skin).mirrorSkin(mirrorSkin)
                .equipment(equipment).nametag(nametag).appearance(appearance()).pose(pose)
                .baby(baby).showInTabList(showInTabList).mobVariant(mobVariant).owner(owner)
                .nametagStyle(nametagStyle)
                .build();
    }

    @Override
    public synchronized Npc owner(UUID playerId) {
        ensureLive();
        this.owner = playerId;
        return this;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public synchronized Npc copy(Location at) {
        ensureLive();
        NpcImpl clone = manager.create(UUID.randomUUID(), name, type, toPosition(at));
        clone.lookAtPlayers = lookAtPlayers;
        clone.clickListener = clickListener;
        clone.skin = skin;
        clone.mirrorSkin = mirrorSkin;
        clone.owner = owner;
        clone.cooldownMillis = cooldownMillis;
        clone.visibleWhen = visibleWhen;
        clone.viewDistance = viewDistance;
        clone.showInTabList = showInTabList;
        clone.nametagStyle = nametagStyle;
        clone.proximityRadius = proximityRadius;
        clone.nearCallback = nearCallback;
        clone.leaveCallback = leaveCallback;
        equipment.forEach(clone::equipment);
        actions.forEach((clickType, entries) ->
                entries.forEach(entry -> clone.addAction(clickType, entry.action(), entry.delayTicks())));
        clone.appearance(appearance());
        clone.pose(pose);
        clone.baby(baby);
        clone.mobVariant(mobVariant);
        if (!nametag.isEmpty()) {
            clone.nametag(nametag);
        }
        return clone;
    }

    private List<HologramLine> hologram() {
        List<String> lines = nametag;
        int[] ids = nametagIds;
        NametagStyle style = nametagStyle;
        if (lines.isEmpty() || ids.length != lines.size()) {
            return List.of();
        }
        List<HologramLine> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            double y = position.y() + HOLOGRAM_BASE + (lines.size() - 1 - i) * HOLOGRAM_SPACING;
            out.add(new HologramLine(ids[i], lines.get(i), position.x(), y, position.z(), style));
        }
        return out;
    }

    synchronized void markRemoved() {
        removed = true;
        endMovement(manager.closed() ? MovementResult.Status.SHUTDOWN : MovementResult.Status.REMOVED);
    }

    private void ensureLive() {
        if (removed || manager.closed()) {
            throw new IllegalStateException("NPC has been removed or its manager is closed");
        }
    }

    static void finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private Npc pushMeta() {
        if (!removed) {
            manager.updateMeta(this);
        }
        return this;
    }

    private void refresh(int[] staleLineIds) {
        if (!removed) {
            manager.refresh(this, staleLineIds);
        }
    }
}
