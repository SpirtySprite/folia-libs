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
import net.folianpc.api.NametagLayout;
import net.folianpc.api.ViewerAppearance;
import net.folianpc.api.NpcPose;
import net.folianpc.api.Skin;
import net.folianpc.api.SkinApplyResult;
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

    private static final int MAX_PROFILE_NAME = 16;
    private static final NavigationOptions DEFAULT_NAVIGATION = NavigationOptions.defaults();

    public record ActionEntry(NpcAction action, long delayTicks) {
    }

    private final UUID uuid;
    private final int entityId;
    private volatile EntityType type;
    private final NpcManager manager;
    private volatile UUID owner;
    private volatile NametagLayout nametagLayout = NametagLayout.defaults();
    private final Map<UUID, ViewerAppearance> viewerAppearances = new ConcurrentHashMap<>();
    private int batchDepth;
    private boolean dirtyRefresh;
    private boolean dirtyMeta;
    private boolean dirtyScale;
    private boolean dirtyEquipment;
    private final Set<EquipmentSlot> dirtySlots = java.util.EnumSet.noneOf(EquipmentSlot.class);
    private boolean dirtyNametag;
    private final Set<Integer> staleLines = new java.util.HashSet<>();

    private volatile Position position;

    private long movementGeneration;
    private MovementRequest movement;
    private NpcBehavior behavior;
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
    private CompletableFuture<SkinApplyResult> pendingSkin;
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
        this.name = name == null || name.isBlank() ? "NPC" : name;
        this.type = type == null ? EntityType.PLAYER : type;
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
        finite(blocksPerSecond, "movement speed");
        String world = target.getWorld() != null ? target.getWorld().getName() : "world";
        if (!world.equals(position.world())) {
            return teleportTo(toPosition(target));
        }
        return walkToward(target.getX(), target.getY(), target.getZ(), blocksPerSecond);
    }

    @Override
    public synchronized CompletableFuture<Boolean> navigateTo(Location target, double blocksPerSecond) {
        endBehavior(MovementResult.Status.SUPERSEDED);
        return manager.navigate(this, target, blocksPerSecond);
    }

    synchronized Npc teleportTo(Position target) {
        endBehavior(MovementResult.Status.SUPERSEDED);
        this.position = target;
        endMovement(MovementResult.Status.SUPERSEDED);
        if (!removed) {
            manager.reposition(this);
        }
        return this;
    }

    synchronized Npc walkToward(double x, double y, double z, double blocksPerSecond) {
        finite(blocksPerSecond, "movement speed");
        new Position(position.world(), x, y, z, 0, 0);
        endBehavior(MovementResult.Status.SUPERSEDED);
        MovementRequest ending = movement;
        movement = null;
        movementGeneration++;
        waypoints.clear();
        this.walkSpeed = Math.max(0.05, blocksPerSecond);
        this.walkTarget = new double[]{x, y, z};
        if (ending != null) ending.finish(MovementResult.of(MovementResult.Status.SUPERSEDED));
        return this;
    }

    @Override
    public synchronized MovementTask navigateTo(Location target, double speed, NavigationOptions options) {
        endBehavior(MovementResult.Status.SUPERSEDED);
        return manager.navigate(this, target, speed, options);
    }

    synchronized MovementRequest beginMovement() {
        MovementRequest ending = movement;
        movementGeneration++;
        walkTarget = null;
        waypoints.clear();
        MovementRequest request = new MovementRequest(movementGeneration, this);
        movement = removed || manager.closed() ? null : request;
        if (movement == null) {
            request.finish(MovementResult.of(manager.closed() ? MovementResult.Status.SHUTDOWN : MovementResult.Status.REMOVED));
        }
        if (ending != null) ending.finish(MovementResult.of(MovementResult.Status.SUPERSEDED));
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
            case RAVAGER -> 2.0;
            case ENDER_DRAGON -> 16;
            case GHAST -> 4;
            case GIANT -> 4;
            case IRON_GOLEM -> 1.4;
            case HORSE, DONKEY, MULE, CAMEL -> 1.7;
            case SPIDER -> 1.4;
            case PLAYER, ZOMBIE, SKELETON, VILLAGER, ENDERMAN -> 0.6;
            default -> 4.0;
        };
        double height = switch (type) {
            case ENDERMAN -> 2.9;
            case IRON_GOLEM -> 2.7;
            case WARDEN -> 2.9;
            case CAMEL -> 2.4;
            case CHICKEN, RABBIT -> 0.7;
            case SPIDER -> 0.9;
            case SLIME, MAGMA_CUBE -> 2.1;
            case ENDER_DRAGON -> 8;
            case GIANT -> 12;
            case GHAST -> 4;
            case PLAYER -> 1.8;
            default -> 4.0;
        };
        var registered = manager.bodySize(type);
        if (registered.isPresent()) {
            width = Math.max(0.0625, registered.get().width());
            height = Math.max(0.0625, registered.get().height());
        }
        boolean living = type.getEntityClass() != null
                && org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass());
        if (pose == NpcPose.SLEEPING && living
                || type == EntityType.PLAYER && (pose == NpcPose.SWIMMING || pose == NpcPose.FALL_FLYING)) {
            height = Math.min(height, 0.6);
        } else if (type == EntityType.PLAYER && pose == NpcPose.CROUCHING) {
            height *= 0.85;
        }
        boolean ageable = type.getEntityClass() != null
                && org.bukkit.entity.Ageable.class.isAssignableFrom(type.getEntityClass());
        double factor = scale * (baby && ageable ? 0.5 : 1.0);
        return new double[]{options.width() > 0 ? options.width() : width * factor,
                options.height() > 0 ? options.height() : height * factor};
    }

    @Override
    public synchronized net.folianpc.api.BehaviorTask patrol(List<Location> points, net.folianpc.api.PatrolOptions options) {
        ensureLive();
        java.util.Objects.requireNonNull(options, "options");
        List<Location> copied = java.util.Objects.requireNonNull(points, "waypoints").stream()
                .map(point -> java.util.Objects.requireNonNull(point, "waypoint").clone()).toList();
        if (copied.isEmpty()) throw new IllegalArgumentException("Patrol needs at least one waypoint");
        copied.forEach(NpcImpl::toPosition);
        endBehavior(MovementResult.Status.SUPERSEDED);
        endMovement(MovementResult.Status.SUPERSEDED);
        behavior = new NpcBehavior(this, manager, copied, options, null, null);
        return behavior;
    }

    @Override
    public synchronized net.folianpc.api.BehaviorTask follow(UUID player, net.folianpc.api.FollowOptions options) {
        ensureLive();
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(options, "options");
        endBehavior(MovementResult.Status.SUPERSEDED);
        endMovement(MovementResult.Status.SUPERSEDED);
        behavior = new NpcBehavior(this, manager, List.of(), null, player, options);
        return behavior;
    }

    synchronized void tickBehavior() {
        if (behavior != null) behavior.tick();
    }

    void clearBehavior(NpcBehavior ending) {
        if (behavior == ending) behavior = null;
    }

    private void endBehavior(MovementResult.Status status) {
        NpcBehavior ending = behavior;
        behavior = null;
        if (ending != null) ending.finish(MovementResult.of(status));
    }

    private void invalidateClearance() {
        endBehavior(MovementResult.Status.SUPERSEDED);
        endMovement(MovementResult.Status.SUPERSEDED);
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
        ensureLive();
        endBehavior(MovementResult.Status.CANCELLED);
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
        String next = name == null || name.isBlank() ? "NPC" : name;
        if (java.util.Objects.equals(this.name, next)) return this;
        this.name = next;
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
        EntityType next = type != null ? type : EntityType.PLAYER;
        if (this.type == next) return this;
        this.type = next;
        invalidateClearance();
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
        boolean changed = !java.util.Objects.equals(this.skin, skin);
        this.skin = skin;
        endSkin(SkinApplyResult.Status.SUPERSEDED);
        if (changed) refresh(nametagIds);
        return this;
    }

    @Override
    public synchronized CompletableFuture<SkinApplyResult> skinAsync(java.util.concurrent.CompletionStage<Skin> fetch) {
        ensureLive();
        java.util.Objects.requireNonNull(fetch, "skin");
        CompletableFuture<SkinApplyResult> ending = pendingSkin;
        CompletableFuture<SkinApplyResult> ticket = new CompletableFuture<>();
        pendingSkin = ticket;
        if (ending != null) ending.complete(new SkinApplyResult(SkinApplyResult.Status.SUPERSEDED, java.util.Optional.empty()));
        fetch.whenComplete((value, failure) -> {
            synchronized (NpcImpl.this) {
                if (pendingSkin != ticket) return;
                pendingSkin = null;
                if (failure != null || value == null) {
                    ticket.complete(new SkinApplyResult(SkinApplyResult.Status.FAILED,
                            java.util.Optional.of(failure == null ? new NullPointerException("Fetched skin") : failure)));
                } else if (removed || manager.closed()) {
                    ticket.complete(new SkinApplyResult(manager.closed() ? SkinApplyResult.Status.SHUTDOWN
                            : SkinApplyResult.Status.REMOVED, java.util.Optional.empty()));
                } else {
                    boolean changed = !java.util.Objects.equals(skin, value);
                    skin = value;
                    if (changed) refresh(nametagIds);
                    ticket.complete(new SkinApplyResult(SkinApplyResult.Status.APPLIED, java.util.Optional.empty()));
                }
            }
        });
        return ticket.copy();
    }

    private void endSkin(SkinApplyResult.Status status) {
        CompletableFuture<SkinApplyResult> ending = pendingSkin;
        pendingSkin = null;
        if (ending != null) ending.complete(new SkinApplyResult(status, java.util.Optional.empty()));
    }

    @Override
    public Skin skin() {
        return skin;
    }

    @Override
    public synchronized Npc mirrorSkin(boolean enabled) {
        ensureLive();
        boolean changed = mirrorSkin != enabled;
        mirrorSkin = enabled;
        endSkin(SkinApplyResult.Status.SUPERSEDED);
        if (changed) refresh(nametagIds);
        return this;
    }

    @Override
    public boolean mirrorSkin() {
        return mirrorSkin;
    }

    @Override
    public synchronized Npc equipment(EquipmentSlot slot, ItemStack item) {
        ensureLive();
        java.util.Objects.requireNonNull(slot, "slot");
        ItemStack next = item == null || item.getType().isAir() ? null : item.clone();
        if (java.util.Objects.equals(equipment.get(slot), next)) return this;
        if (next == null) {
            equipment.remove(slot);
        } else {
            equipment.put(slot, next);
        }
        if (!removed) {
            publishEquipment(slot);
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
        if (nametag.equals(clean) && nametagVisible == clean.isEmpty()) return this;
        boolean respawn = clean.size() != nametagIds.length || nametagVisible != clean.isEmpty();
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
        if (respawn) refresh(stale); else publishNametag();
        return this;
    }

    @Override
    public List<String> nametag() {
        return nametag;
    }

    @Override
    public synchronized Npc nametagVisible(boolean visible) {
        ensureLive();
        if (java.util.Objects.equals(this.nametagVisible, visible)) return this;
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
            publishNametag();
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
        if (java.util.Objects.equals(this.glowing, value)) return this;
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
        if (java.util.Objects.equals(this.invisible, value)) return this;
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
        if (java.util.Objects.equals(this.skinLayers, value)) return this;
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
        double next = Math.max(0.0625, value);
        if (scale == next) return this;
        this.scale = next;
        invalidateClearance();
        if (!removed) {
            publishScale();
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
        NpcPose next = pose == null ? NpcPose.STANDING : pose;
        if (this.pose == next) return this;
        this.pose = next;
        invalidateClearance();
        publishNametag();
        return pushMeta();
    }

    @Override
    public NpcPose pose() {
        return pose;
    }

    @Override
    public synchronized Npc baby(boolean value) {
        ensureLive();
        if (java.util.Objects.equals(this.baby, value)) return this;
        this.baby = value;
        invalidateClearance();
        publishNametag();
        return pushMeta();
    }

    @Override
    public boolean baby() {
        return baby;
    }

    @Override
    public synchronized Npc variant(int value) {
        ensureLive();
        if (java.util.Objects.equals(mobVariant.variant(), value)) return this;
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
        if (java.util.Objects.equals(mobVariant.variantName(), name)) return this;
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
        if (java.util.Objects.equals(mobVariant.villagerProfession(), profession)) return this;
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
        if (java.util.Objects.equals(mobVariant.villagerType(), biomeType)) return this;
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
        if (java.util.Objects.equals(mobVariant.villagerLevel(), level)) return this;
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
        MobVariant next = variant == null ? MobVariant.defaults() : variant;
        if (mobVariant.equals(next)) return this;
        this.mobVariant = next;
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
            publishNametag();
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
        if (java.util.Objects.equals(this.glowColor, color)) return this;
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
        if (java.util.Objects.equals(this.collidable, value)) return this;
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
        if (java.util.Objects.equals(this.showInTabList, value)) return this;
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
        java.util.Objects.requireNonNull(appearance, "appearance");
        if (appearance().equals(appearance)) return this;
        boolean respawn = !java.util.Objects.equals(glowColor, appearance.glowColor())
                || collidable != appearance.collidable() || nametagVisible != appearance.nametagVisible();
        boolean metaChanged = glowing != appearance.glowing() || invisible != appearance.invisible()
                || skinLayers != appearance.skinLayers();
        boolean clearanceChanged = scale != appearance.scale();
        this.glowing = appearance.glowing();
        this.invisible = appearance.invisible();
        this.skinLayers = appearance.skinLayers();
        this.scale = appearance.scale();
        this.glowColor = appearance.glowColor();
        this.collidable = appearance.collidable();
        this.nametagVisible = appearance.nametagVisible();
        if (clearanceChanged) invalidateClearance();
        if (respawn) refresh(nametagIds);
        else {
            if (metaChanged) pushMeta();
            if (clearanceChanged) publishScale();
        }
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
        viewerAppearances.remove(playerId);
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
                equipment, hologram(), Map.copyOf(rawMeta));
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
        clone.visibilityHysteresis = visibilityHysteresis;
        clone.nametagLayout = nametagLayout;
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
        double baseY = position.y() + nametagLayout.offset()
                + (nametagLayout.entityRelative() ? dimensions(DEFAULT_NAVIGATION)[1] : 0);
        for (int i = 0; i < lines.size(); i++) {
            double y = baseY + (lines.size() - 1 - i) * nametagLayout.spacing();
            out.add(new HologramLine(ids[i], lines.get(i), position.x(), y, position.z(), style));
        }
        return out;
    }

    synchronized void markRemoved() {
        removed = true;
        endBehavior(manager.closed() ? MovementResult.Status.SHUTDOWN : MovementResult.Status.REMOVED);
        endSkin(manager.closed() ? SkinApplyResult.Status.SHUTDOWN : SkinApplyResult.Status.REMOVED);
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

    @Override
    public synchronized Npc batch(Consumer<Npc> updates) {
        ensureLive();
        java.util.Objects.requireNonNull(updates, "updates");
        batchDepth++;
        try {
            updates.accept(this);
        } finally {
            if (--batchDepth == 0) flushAppearance();
        }
        return this;
    }

    private void publishScale() {
        if (batchDepth > 0) dirtyScale = true; else manager.updateScale(this);
        publishNametag();
    }

    private void publishEquipment(EquipmentSlot slot) {
        if (batchDepth > 0) {
            dirtyEquipment = true;
            dirtySlots.add(slot);
        } else manager.updateEquipment(this, Set.of(slot));
    }

    private void publishNametag() {
        if (batchDepth > 0) dirtyNametag = true; else manager.updateNametag(this);
    }

    private void flushAppearance() {
        boolean respawn = dirtyRefresh;
        boolean meta = dirtyMeta;
        boolean scaleUpdate = dirtyScale;
        boolean equipmentUpdate = dirtyEquipment;
        Set<EquipmentSlot> changedSlots = Set.copyOf(dirtySlots);
        dirtySlots.clear();
        boolean nametagUpdate = dirtyNametag;
        int[] stale = staleLines.stream().mapToInt(Integer::intValue).toArray();
        dirtyRefresh = dirtyMeta = dirtyScale = dirtyEquipment = dirtyNametag = false;
        staleLines.clear();
        if (removed || manager.closed()) return;
        if (respawn) {
            manager.refresh(this, stale);
        } else {
            if (meta) manager.updateMeta(this);
            if (scaleUpdate) manager.updateScale(this);
            if (equipmentUpdate) manager.updateEquipment(this, changedSlots);
            if (nametagUpdate) manager.updateNametag(this);
        }
    }

    @Override
    public synchronized Npc nametagLayout(NametagLayout layout) {
        ensureLive();
        java.util.Objects.requireNonNull(layout, "layout");
        if (!nametagLayout.equals(layout)) {
            nametagLayout = layout;
            publishNametag();
        }
        return this;
    }

    @Override public NametagLayout nametagLayout() { return nametagLayout; }

    @Override
    public synchronized Npc appearanceFor(UUID viewer, ViewerAppearance appearance) {
        ensureLive();
        java.util.Objects.requireNonNull(viewer, "viewer");
        java.util.Objects.requireNonNull(appearance, "appearance");
        if (appearance.equals(viewerAppearances.get(viewer))) return this;
        viewerAppearances.put(viewer, appearance);
        manager.refreshViewer(this, viewer);
        return this;
    }

    @Override
    public synchronized Npc clearAppearanceFor(UUID viewer) {
        ensureLive();
        if (viewerAppearances.remove(java.util.Objects.requireNonNull(viewer, "viewer")) != null) {
            manager.refreshViewer(this, viewer);
        }
        return this;
    }

    public synchronized NpcSnapshot snapshot(UUID viewer) {
        NpcSnapshot base = snapshot();
        ViewerAppearance override = viewerAppearances.get(viewer);
        if (override == null) return base;
        NpcAppearance appearance = override.appearance().orElseGet(this::appearance);
        Map<EquipmentSlot, ItemStack> equipment = new java.util.EnumMap<>(EquipmentSlot.class);
        equipment.putAll(base.equipment());
        override.equipment().forEach((slot, item) -> {
            if (item.getType().isAir()) equipment.remove(slot); else equipment.put(slot, item);
        });
        boolean team = !appearance.nametagVisible() || appearance.glowColor() != null || !appearance.collidable();
        String wireName = team ? uuid.toString().replace("-", "") : name;
        String profile = wireName.length() > MAX_PROFILE_NAME ? wireName.substring(0, MAX_PROFILE_NAME) : wireName;
        double shift = nametagLayout.entityRelative()
                ? dimensions(DEFAULT_NAVIGATION)[1] * (appearance.scale() / scale - 1) : 0;
        List<HologramLine> lines = base.hologram().stream().map(line -> new HologramLine(line.entityId(),
                line.text(), line.x(), line.y() + shift, line.z(), line.style())).toList();
        return new NpcSnapshot(base.entityId(), base.uuid(), base.name(), profile,
                appearance.nametagVisible(), appearance.glowing(), appearance.invisible(), appearance.skinLayers(),
                appearance.scale(), appearance.glowColor() == null ? null : appearance.glowColor().toString(),
                appearance.collidable(), team, base.pose(), base.baby(), base.mobVariant(), base.type(), base.world(),
                base.x(), base.y(), base.z(), base.yaw(), base.pitch(), base.skinValue(), base.skinSignature(),
                base.mirrorSkin(), base.showInTabList(), equipment, lines, base.rawMeta());
    }

    private Npc pushMeta() {
        if (!removed) {
            if (batchDepth > 0) dirtyMeta = true; else manager.updateMeta(this);
        }
        return this;
    }

    private void refresh(int[] staleLineIds) {
        if (!removed) {
            if (batchDepth > 0) {
                dirtyRefresh = true;
                for (int id : staleLineIds) staleLines.add(id);
            } else {
                manager.refresh(this, staleLineIds);
            }
        }
    }
}
