package io.github.spirtysprite.integration.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTabListPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;

/**
 * Joins a server as an ordinary player and writes down what the server sent it.
 *
 * <p>Usage: {@code Bot <host> <port> <result-file> [name] [max-seconds]}. The bot stays until the server disconnects
 * it or the time runs out, then writes a JSON file with the packets it saw.
 */
public final class Bot {

    private static final int LIMIT = 200;

    private final Map<String, Integer> packetCounts = new TreeMap<>();
    private final List<String> objectives = new ArrayList<>();
    private final List<String> displays = new ArrayList<>();
    private final List<String> scores = new ArrayList<>();
    private final List<String> teams = new ArrayList<>();
    private final List<String> entities = new ArrayList<>();
    private final List<String> playerInfo = new ArrayList<>();
    private final List<String> screens = new ArrayList<>();
    private final List<String> contents = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private final List<String> presentationEvents = new ArrayList<>();
    private final Map<String, String> activeObjectives = new TreeMap<>();
    private final Map<String, String> activeTeams = new TreeMap<>();
    private final Map<UUID, String> activeBossBars = new HashMap<>();
    private final Map<String, UUID> uuidByName = new HashMap<>();
    private final Map<UUID, Integer> entityByUuid = new HashMap<>();
    private final ScheduledExecutorService delayed = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bot-actions");
        thread.setDaemon(true);
        return thread;
    });
    private boolean clickedMenu;
    private boolean interactedWithNpc;
    private boolean joined;
    private boolean npcEquipmentSeen;
    private boolean npcEquipmentCleared;
    private final java.util.Set<Integer> equippedEntities = new java.util.HashSet<>();
    private String disconnectReason = "";
    private final CountDownLatch finished = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: Bot <host> <port> <result-file> [name] [max-seconds]");
            System.exit(2);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path resultFile = Path.of(args[2]);
        String name = args.length > 3 ? args[3] : "ItBot";
        long maxSeconds = args.length > 4 ? Long.parseLong(args[4]) : 90;

        Bot bot = new Bot();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bot-packets");
            thread.setDaemon(true);
            return thread;
        });
        Session session = createSession(host, port, name, executor);
        session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session source, Packet packet) {
                bot.onPacket(source, packet);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                synchronized (bot) {
                    bot.disconnectReason = event.getReason() == null ? "" : plain(event.getReason());
                    if (event.getCause() != null) {
                        bot.disconnectReason += " [" + event.getCause() + "]";
                    }
                }
                bot.finished.countDown();
            }
        });
        session.getClass().getMethod("connect", boolean.class).invoke(session, true);
        boolean disconnected = bot.finished.await(maxSeconds, TimeUnit.SECONDS);
        if (!disconnected) {
            session.disconnect(Component.text("bot timeout"));
        }
        bot.write(resultFile, disconnected);
        System.exit(0);
    }

    private synchronized void onPacket(Session session, Packet packet) {
        packetCounts.merge(packet.getClass().getSimpleName(), 1, Integer::sum);
        if (packet instanceof ClientboundLoginPacket) {
            joined = true;
        } else if (packet instanceof ClientboundSetObjectivePacket p) {
            add(objectives, p.getName() + " | " + p.getAction() + " | " + plain(p.getDisplayName()));
            if (p.getAction().name().equals("REMOVE")) {
                activeObjectives.remove(p.getName());
            } else {
                activeObjectives.put(p.getName(), plain(p.getDisplayName()));
            }
        } else if (packet instanceof ClientboundSetDisplayObjectivePacket p) {
            add(displays, p.getPosition() + " | " + p.getName());
        } else if (packet instanceof ClientboundSetScorePacket p) {
            add(scores, p.getObjective() + " | " + p.getOwner() + " | " + p.getValue() + " | " + plain(p.getDisplay()));
        } else if (packet instanceof ClientboundSetPlayerTeamPacket p) {
            add(teams, p.getTeamName() + " | " + p.getAction() + " | " + plain(p.getPrefix()) + plain(p.getSuffix())
                    + " | players=" + (p.getPlayers() == null ? 0 : p.getPlayers().length));
            if (p.getAction().name().equals("REMOVE")) {
                activeTeams.remove(p.getTeamName());
            } else if (p.getAction().name().equals("CREATE") || p.getAction().name().equals("UPDATE")) {
                activeTeams.put(p.getTeamName(), plain(p.getPrefix()));
            }
        } else if (packet instanceof ClientboundBossEventPacket p) {
            String action = p.getAction().name();
            add(presentationEvents, "boss | " + p.getUuid() + " | " + action + " | " + plain(p.getTitle()));
            if (action.equals("REMOVE")) {
                activeBossBars.remove(p.getUuid());
            } else if (action.equals("ADD") || action.equals("UPDATE_TITLE")) {
                activeBossBars.put(p.getUuid(), plain(p.getTitle()));
            }
        } else if (packet instanceof ClientboundTabListPacket p) {
            add(presentationEvents, "tab | " + plain(p.getHeader()) + " | " + plain(p.getFooter()));
        } else if (packet.getClass().getSimpleName().equals("ClientboundAddEntityPacket")) {
            UUID uuid = (UUID) property(packet, "getUuid");
            int entityId = ((Number) property(packet, "getEntityId")).intValue();
            add(entities, property(packet, "getType") + " | " + uuid + " | id=" + entityId);
            entityByUuid.put(uuid, entityId);
            interactWithNpcWhenKnown(session);
        } else if (packet instanceof ClientboundSetEquipmentPacket p) {
            boolean filled = java.util.Arrays.stream(p.getEquipment())
                    .anyMatch(equipment -> equipment.getItem() != null && equipment.getItem().getAmount() > 0);
            if (filled) {
                equippedEntities.add(p.getEntityId());
                npcEquipmentSeen = true;
            } else if (equippedEntities.remove(p.getEntityId())) {
                npcEquipmentCleared = true;
            }
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket p) {
            for (PlayerListEntry entry : p.getEntries()) {
                String profileName = entry.getProfile() == null ? "?" : entry.getProfile().getName();
                add(playerInfo, p.getActions() + " | " + profileName + " | " + entry.getProfileId());
                uuidByName.put(profileName, entry.getProfileId());
            }
            interactWithNpcWhenKnown(session);
        } else if (packet instanceof ClientboundOpenScreenPacket p) {
            add(screens, "container=" + p.getContainerId() + " | " + p.getType() + " | " + plain(p.getTitle()));
        } else if (packet instanceof ClientboundContainerSetContentPacket p) {
            int filled = 0;
            for (ItemStack item : p.getItems()) {
                if (item != null && item.getAmount() > 0) {
                    filled++;
                }
            }
            add(contents, "container=" + p.getContainerId() + " | slots=" + p.getItems().length + " | filled=" + filled);
            if (p.getContainerId() != 0 && filled > 0 && !clickedMenu) {
                clickedMenu = true;
                int container = p.getContainerId();
                int state = p.getStateId();
                // Click the item in the middle of the three row menu, as a player would.
                delayed.schedule(() -> {
                    session.send(new ServerboundContainerClickPacket(container, state, 13, ContainerActionType.CLICK_ITEM,
                            ClickItemAction.LEFT_CLICK, null, new Int2ObjectOpenHashMap<>()));
                    recordAction("clicked slot 13 of container " + container);
                }, 600, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static Session createSession(String host, int port, String name, ExecutorService executor) throws ReflectiveOperationException {
        MinecraftProtocol protocol = new MinecraftProtocol(name);
        try {
            Class<?> type = Class.forName("org.geysermc.mcprotocollib.network.session.ClientNetworkSession");
            Class<?> proxy = Class.forName("org.geysermc.mcprotocollib.network.ProxyInfo");
            return (Session) type.getConstructor(java.net.SocketAddress.class, MinecraftProtocol.class,
                    java.util.concurrent.Executor.class, java.net.SocketAddress.class, proxy)
                    .newInstance(new InetSocketAddress(host, port), protocol, executor, null, null);
        } catch (ClassNotFoundException absent) {
            Class<?> type = Class.forName("org.geysermc.mcprotocollib.network.tcp.TcpClientSession");
            Class<?> protocolType = Class.forName("org.geysermc.mcprotocollib.network.packet.PacketProtocol");
            return (Session) type.getConstructor(String.class, int.class, protocolType).newInstance(host, port, protocol);
        }
    }

    private static Object property(Packet packet, String getter) {
        try {
            return packet.getClass().getMethod(getter).invoke(packet);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read " + getter + " from " + packet.getClass().getName(), failure);
        }
    }

    /** Once the NPC's player entry and entity are both known, right click it and then left click it. */
    private void interactWithNpcWhenKnown(Session session) {
        UUID npc = uuidByName.get("ItNpc");
        Integer entityId = npc == null ? null : entityByUuid.get(npc);
        if (entityId == null || interactedWithNpc) {
            return;
        }
        interactedWithNpc = true;
        int id = entityId;
        delayed.schedule(() -> {
            session.send(new ServerboundInteractPacket(id, InteractAction.INTERACT, Hand.MAIN_HAND, false));
            recordAction("right clicked entity " + id);
        }, 1200, TimeUnit.MILLISECONDS);
        delayed.schedule(() -> {
            session.send(new ServerboundInteractPacket(id, InteractAction.ATTACK, false));
            recordAction("left clicked entity " + id);
        }, 2000, TimeUnit.MILLISECONDS);
    }

    private synchronized void recordAction(String what) {
        add(actions, what);
    }

    private static void add(List<String> list, String entry) {
        if (list.size() < LIMIT) {
            list.add(entry);
        }
    }

    /** The visible text of a component, without formatting. Enough to recognise titles and lines. */
    static String plain(Component component) {
        if (component == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent t) {
            text.append(t.content());
        } else if (component instanceof TranslatableComponent t) {
            text.append('<').append(t.key()).append('>');
        }
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }

    private synchronized void write(Path file, boolean disconnectedByServer) throws IOException {
        Map<String, Object> result = new TreeMap<>();
        result.put("joined", joined);
        result.put("npcEquipmentSeen", npcEquipmentSeen);
        result.put("npcEquipmentCleared", npcEquipmentCleared);
        result.put("disconnectedByServer", disconnectedByServer);
        result.put("disconnectReason", disconnectReason);
        result.put("packetCounts", packetCounts);
        result.put("objectives", objectives);
        result.put("displays", displays);
        result.put("scores", scores);
        result.put("teams", teams);
        result.put("entities", entities);
        result.put("playerInfo", playerInfo);
        result.put("screens", screens);
        result.put("containerContents", contents);
        result.put("actions", actions);
        result.put("presentationEvents", presentationEvents);
        result.put("activeObjectives", activeObjectives);
        result.put("activeTeams", activeTeams);
        result.put("activeBossBars", activeBossBars);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(file, gson.toJson(result), StandardCharsets.UTF_8);
    }
}
