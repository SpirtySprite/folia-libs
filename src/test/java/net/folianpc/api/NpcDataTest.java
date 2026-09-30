package net.folianpc.api;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDataTest {

    private static NpcData sample() {
        return NpcData.builder()
                .id(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"))
                .name("Shopkeeper")
                .type(EntityType.VILLAGER)
                .position("world_nether", 10.5, 64, -3.25, 90f, -15f)
                .lookAtPlayers(true)
                .skin(Skin.of("VALUE", "SIGNATURE"))
                .mirrorSkin(true)
                .nametag("<gold>Shop", "<gray>Right-click")
                .appearance(new NpcAppearance(true, false, false, 1.5, NamedTextColor.GOLD, false, true))
                .pose(NpcPose.CROUCHING)
                .baby(true)
                .showInTabList(true)
                .mobVariant(new MobVariant(2, "desert", "librarian", "desert", 4))
                .owner(UUID.fromString("11111111-2222-3333-4444-555555555555"))
                .nametagStyle(NametagStyle.defaults().withBackground(0x220044, 90).withSeeThrough(true))
                .build();
    }

    @Test
    void builderDefaultsMatchAFreshNpc() {
        NpcData data = NpcData.builder().position("world", 1, 2, 3, 0, 0).build();

        assertNull(data.id());
        assertEquals("NPC", data.name());
        assertEquals(EntityType.PLAYER, data.type());
        assertEquals(NpcAppearance.defaults(), data.appearance());
        assertEquals(NpcPose.STANDING, data.pose());
        assertEquals(MobVariant.defaults(), data.mobVariant());
        assertEquals(NametagStyle.defaults(), data.nametagStyle());
        assertTrue(data.equipment().isEmpty());
        assertTrue(data.nametag().isEmpty());
        assertFalse(data.lookAtPlayers());
    }

    @Test
    void builderNullsFallBackToDefaultsInsteadOfBreaking() {
        NpcData data = NpcData.builder()
                .type(null).appearance(null).pose(null).mobVariant(null).nametagStyle(null).nametag((List<String>) null)
                .build();

        assertEquals(EntityType.PLAYER, data.type());
        assertEquals(NpcPose.STANDING, data.pose());
        assertEquals(NpcAppearance.defaults(), data.appearance());
        assertTrue(data.nametag().isEmpty());
    }

    @Test
    void toBuilderCopiesEverythingAndLetsYouChangeOneField() {
        NpcData original = sample();

        assertEquals(original, original.toBuilder().build());

        NpcData renamed = original.toBuilder().name("Banker").build();
        assertEquals("Banker", renamed.name());
        assertEquals(original.skin(), renamed.skin());
        assertEquals(original.mobVariant(), renamed.mobVariant());
        assertNotEquals(original, renamed);
    }

    @Test
    @SuppressWarnings("deprecation")
    void theOldLongConstructorStillWorks() {
        NpcData legacy = new NpcData(UUID.randomUUID(), "Bob", EntityType.PLAYER, "world", 0, 64, 0, 0, 0,
                false, null, false, Map.of(), List.of("hi"), NpcAppearance.defaults(), NpcPose.STANDING,
                false, false, MobVariant.defaults(), null);

        assertEquals(NametagStyle.defaults(), legacy.nametagStyle());
    }

    @Test
    void serializeThenDeserializeGivesTheSameData() {
        NpcData original = sample();

        NpcData restored = NpcData.deserialize(original.serialize());

        assertEquals(original, restored);
    }

    @Test
    void theSerializedFormIsPlainAndVersioned() {
        Map<String, Object> map = sample().serialize();

        assertEquals(NpcDataCodec.SCHEMA, map.get("schema"));
        assertEquals("VILLAGER", map.get("type"));
        assertEquals("853c80ef-3c37-49fd-aa49-938b674adae6", map.get("id"));
        assertTrue(map.get("skin") instanceof Map<?, ?>);
        assertTrue(map.get("nametag") instanceof List<?>);
        assertEquals("gold", ((Map<?, ?>) map.get("appearance")).get("glowColor"));
    }

    @Test
    void unsignedSkinsAndMissingOptionalFieldsSurvive() {
        NpcData original = NpcData.builder().position("world", 0, 0, 0, 0, 0).skin(Skin.of("V", null)).build();

        NpcData restored = NpcData.deserialize(original.serialize());

        assertEquals(original, restored);
        assertNull(restored.skin().signature());
        assertNull(restored.id());
        assertNull(restored.owner());
    }

    @Test
    void emptyInputGivesDefaultsInsteadOfFailing() {
        NpcData data = NpcData.deserialize(Map.of());

        assertEquals("NPC", data.name());
        assertEquals(EntityType.PLAYER, data.type());
        assertEquals("", data.world());
    }

    @Test
    void valuesAsYamlOrJsonWouldReturnThemAreAccepted() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "FromYaml");
        map.put("world", "world");
        map.put("x", 10);
        map.put("y", "64.5");
        map.put("z", 3L);
        map.put("yaw", 90);
        map.put("lookAtPlayers", "true");
        map.put("nametag", new ArrayList<>(List.of("a", 7)));
        map.put("appearance", Map.of("scale", 2, "glowing", true));

        NpcData data = NpcData.deserialize(map);

        assertEquals(10.0, data.x());
        assertEquals(64.5, data.y());
        assertEquals(3.0, data.z());
        assertEquals(90f, data.yaw());
        assertTrue(data.lookAtPlayers());
        assertEquals(List.of("a", "7"), data.nametag());
        assertEquals(2.0, data.appearance().scale());
        assertTrue(data.appearance().glowing());
        assertTrue(data.appearance().skinLayers(), "fields missing from a section keep their defaults");
    }

    @Test
    void unknownKeysAreIgnoredAndNewerSchemasWarn() {
        Map<String, Object> map = sample().serialize();
        map.put("schema", NpcDataCodec.SCHEMA + 1);
        map.put("somethingFromTheFuture", Map.of("a", 1));
        List<String> warnings = new ArrayList<>();

        NpcData data = NpcDataCodec.fromMap(map, warnings::add);

        assertEquals(sample(), data);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("schema"));
    }

    @Test
    void unknownEnumNamesFallBackAndAreReported() {
        Map<String, Object> map = sample().serialize();
        map.put("type", "SPACE_CRAB");
        map.put("pose", "MOONWALKING");
        List<String> warnings = new ArrayList<>();

        NpcData data = NpcDataCodec.fromMap(map, warnings::add);

        assertEquals(EntityType.PLAYER, data.type());
        assertEquals(NpcPose.STANDING, data.pose());
        assertEquals(2, warnings.size());
    }

    @Test
    void anInvalidUuidIsDroppedWithAWarningInsteadOfThrowing() {
        Map<String, Object> map = sample().serialize();
        map.put("id", "not-a-uuid");
        List<String> warnings = new ArrayList<>();

        NpcData data = NpcDataCodec.fromMap(map, warnings::add);

        assertNull(data.id());
        assertEquals(1, warnings.size());
    }

    @Test
    void unknownGlowColourIsReported() {
        Map<String, Object> map = sample().serialize();
        ((Map<String, Object>) map.get("appearance")).put("glowColor", "chartreuse");
        List<String> warnings = new ArrayList<>();

        NpcData data = NpcDataCodec.fromMap(map, warnings::add);

        assertNull(data.appearance().glowColor());
        assertEquals(1, warnings.size());
    }

    @Test
    void anUnreadableEquipmentSlotIsSkippedNotFatal() {
        Map<String, Object> map = sample().serialize();
        map.put("equipment", Map.of("HEAD", Map.of("garbage", true), "NOT_A_SLOT", Map.of("x", 1)));
        List<String> warnings = new ArrayList<>();

        NpcData data = NpcDataCodec.fromMap(map, warnings::add);

        assertTrue(data.equipment().isEmpty());
        assertFalse(warnings.isEmpty());
    }
}
