package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UUIDUtilsTest {

    @Test
    void onlineModeUuidIsVersion4() {
        assertTrue(UUIDUtils.isOnlineMode(UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48")));
        assertTrue(UUIDUtils.isOnlineMode(UUID.randomUUID()));
    }

    @Test
    void offlineModeUuidIsVersion3() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        assertFalse(UUIDUtils.isOnlineMode(offline));
    }

    @Test
    void nullUuidIsNotOnlineMode() {
        assertFalse(UUIDUtils.isOnlineMode(null));
    }

    @Test
    void uuidv7CarriesVersionAndVariantBitsForRfc9562() {
        UUID uuid = UUIDUtils.uuidv7();
        assertEquals(7, uuid.version(), "version nibble must be 0111");
        assertEquals(2, uuid.variant(), "variant bits must mark RFC 9562");
        assertFalse(UUIDUtils.isOnlineMode(uuid), "a v7 uuid is structurally distinct from a Mojang v4 identity");
    }

    @Test
    void uuidv7TimestampsUsesTheClockPrefix() {
        long before = System.currentTimeMillis();
        UUID uuid = UUIDUtils.uuidv7();
        long after = System.currentTimeMillis();
        long embeddedMs = uuid.getMostSignificantBits() >>> 16;
        assertTrue(embeddedMs >= before - 1000, "embedded timestamp must not precede the call");
        assertTrue(embeddedMs <= after + 1000, "embedded timestamp must not be in the future");
    }

    @Test
    void uuidv7ValuesAreRoughlyTimeOrdered() {
        // Same-ms twins are expected; the clock prefix must never move backwards by more
        // than a negligible clock adjustment across a burst.
        UUID first = UUIDUtils.uuidv7();
        for (int i = 0; i < 10_000; i++) {
            UUID next = UUIDUtils.uuidv7();
            long firstMs = first.getMostSignificantBits() >>> 16;
            long nextMs = next.getMostSignificantBits() >>> 16;
            assertTrue(nextMs >= firstMs - 1, "burst must stay time-ordered modulo clock skew");
            first = next;
        }
    }

    @Test
    void uuidv7GeneratesDistinctValues() {
        // The rand fields must vary: with a constant/counter-only rand, two servers discovered
        // within the same millisecond would get identical keys and break the PK upsert.
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            uuids.add(UUIDUtils.uuidv7());
        }
        assertEquals(10, uuids.size(), "v7 rand fields must vary");
    }
}