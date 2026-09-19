package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
}