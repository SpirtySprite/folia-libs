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
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;

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
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
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
        ClientNetworkSession session = new ClientNetworkSession(new InetSocketAddress(host, port),
                new MinecraftProtocol(name), executor, null, null);
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
        session.connect(true);
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
        } else if (packet instanceof ClientboundSetDisplayObjectivePacket p) {
            add(displays, p.getPosition() + " | " + p.getName());
        } else if (packet instanceof ClientboundSetScorePacket p) {
            add(scores, p.getObjective() + " | " + p.getOwner() + " | " + p.getValue() + " | " + plain(p.getDisplay()));
        } else if (packet instanceof ClientboundSetPlayerTeamPacket p) {
            add(teams, p.getTeamName() + " | " + p.getAction() + " | " + plain(p.getPrefix()) + plain(p.getSuffix())
                    + " | players=" + p.getPlayers().length);
        } else if (packet instanceof ClientboundAddEntityPacket p) {
            add(entities, p.getType() + " | " + p.getUuid() + " | id=" + p.getEntityId());
            entityByUuid.put(p.getUuid(), p.getEntityId());
            interactWithNpcWhenKnown(session);
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
                            ClickItemAction.LEFT_CLICK, null, new Int2ObjectOpenHashMap<HashedStack>()));
                    recordAction("clicked slot 13 of container " + container);
                }, 600, TimeUnit.MILLISECONDS);
            }
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(file, gson.toJson(result), StandardCharsets.UTF_8);
    }
}
