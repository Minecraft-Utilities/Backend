package xyz.mcutils.backend.model.domain.server.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises {@link JavaVersion#detailedCopy()} platform derivation: color-coded version names
 * (legacy {@code §4}, modern hex {@code §x…}, modern {@code §#…}) must not leak codes into the
 * platform bucket, and a fully colored first word collapses to {@code unknown}.
 */
class JavaVersionTest {

    @Test
    void stripsLegacyColorCodesFromPlatform() {
        JavaVersion version = new JavaVersion("§4Paper 1.21.4", 769, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertEquals("Paper", detailed.getPlatform());
    }

    @Test
    void stripsModernHexColorCodesFromPlatform() {
        JavaVersion version = new JavaVersion("§x§F§F§A§A§0§0Spigot 1.20.1", 763, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertEquals("Spigot", detailed.getPlatform());
    }

    @Test
    void stripsSharpHexColorCodesFromPlatform() {
        JavaVersion version = new JavaVersion("§#FF55AAVelocity 3.3.0", 769, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertEquals("Velocity", detailed.getPlatform());
    }

    @Test
    void fullyColoredFirstWordCollapsesToNullPlatform() {
        JavaVersion version = new JavaVersion("§4 1.21.4", 769, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertNull(detailed.getPlatform());
    }

    @Test
    void keepsUncoloredTwoWordPlatform() {
        JavaVersion version = new JavaVersion("Paper 1.21.4", 769, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertEquals("Paper", detailed.getPlatform());
    }

    @Test
    void plainVersionNameYieldsNullPlatform() {
        JavaVersion version = new JavaVersion("1.21.4", 769, null, null);
        JavaVersion detailed = version.detailedCopy();
        assertNull(detailed.getPlatform());
    }
}