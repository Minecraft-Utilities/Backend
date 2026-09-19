package xyz.mcutils.backend.service.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ipv4SpaceTest {

    private Ipv4Space space(long seed) {
        return new Ipv4Space(List.of(), List.of(), seed);
    }

    @Test
    void defaultExclusionsCoverPrivateAndReservedRanges() {
        Ipv4Space space = space(1);
        assertTrue(space.isExcluded(ip(10, 0, 0, 0)));
        assertTrue(space.isExcluded(ip(127, 0, 0, 0)));
        assertTrue(space.isExcluded(ip(172, 31, 0, 0)));
        assertTrue(space.isExcluded(ip(192, 168, 1, 0)));
        assertTrue(space.isExcluded(ip(169, 254, 0, 0)));
        assertTrue(space.isExcluded(ip(100, 64, 0, 0)));
        assertTrue(space.isExcluded(ip(224, 0, 0, 0)));  // multicast
        assertTrue(space.isExcluded(ip(240, 0, 0, 0)));  // reserved
        assertTrue(space.isExcluded(ip(6, 0, 0, 0)));    // DoD
        assertTrue(space.isExcluded(ip(214, 1, 0, 0)));  // DoD
    }

    @Test
    void publicAddressesAreNotExcluded() {
        Ipv4Space space = space(1);
        assertFalse(space.isExcluded(ip(8, 8, 8, 0)));
        assertFalse(space.isExcluded(ip(1, 1, 1, 0)));
        assertFalse(space.isExcluded(ip(4, 4, 4, 0)));
        assertFalse(space.isExcluded(ip(9, 9, 9, 0)));
    }

    @Test
    void yieldedSubnetsAreNeverExcluded() {
        Ipv4Space space = space(42);
        for (int i = 0; i < 500; i++) {
            long base = space.next24();
            assertTrue(base >= 0, "space exhausted unexpectedly");
            assertFalse(space.isExcluded(base), "yielded excluded subnet " + Ipv4Space.longToIpv4(base));
        }
    }

    @Test
    void sameSeedProducesSameOrder() {
        Ipv4Space a = space(7);
        Ipv4Space b = space(7);
        for (int i = 0; i < 25; i++) {
            assertEquals(a.next24(), b.next24());
        }
    }

    @Test
    void resumesExactlyFromProgress() {
        Ipv4Space original = space(99);
        for (int i = 0; i < 5; i++) {
            assertTrue(original.next24() >= 0);
        }
        Ipv4Space.Progress progress = original.progress(); // position after the 5th /24
        long expectedSixth = original.next24();

        Ipv4Space resumed = space(99);
        resumed.restore(progress);
        assertEquals(expectedSixth, resumed.next24());
    }

    @Test
    void scopedModeScansOnlyIncludedCidr() {
        Ipv4Space test = new Ipv4Space(List.of(), List.of("127.0.0.1/32"), 1);
        assertTrue(test.hasIncludeScope());
        assertEquals(ip(127, 0, 0, 0), test.next24());
        assertEquals(-1, test.next24(), "scoped mode must be exhausted after the included /24");
    }

    @Test
    void extraExclusionsAreRespected() {
        Ipv4Space space = new Ipv4Space(List.of("8.8.8.0/24"), List.of(), 1);
        assertTrue(space.isExcluded(ip(8, 8, 8, 0)));
        assertFalse(space.isExcluded(ip(8, 8, 4, 0)));
    }

    @Test
    void hostsSkipNetworkAndBroadcastAddresses() {
        long[] hosts = Ipv4Space.hostsIn24(ip(8, 8, 8, 0), 5);
        assertEquals(254, hosts.length);
        for (long host : hosts) {
            assertTrue(host >= ip(8, 8, 8, 1));
            assertTrue(host <= ip(8, 8, 8, 254));
        }
    }

    @Test
    void parseCidrComputesRangeBounds() {
        Ipv4Space.Cidr cidr = Ipv4Space.parseCidr("192.168.1.0/24");
        assertEquals(ip(192, 168, 1, 0), cidr.start());
        assertEquals(ip(192, 168, 1, 255), cidr.endInclusive());
    }

    @Test
    void public24CountReflectsExclusionCoverage() {
        // 2^24 total /24s minus the default exclusion ranges (~2.97M /24s) -> ~13.8M public.
        long count = space(1).public24Count();
        assertTrue(count > 13_000_000 && count < 16_777_216, "unexpected public /24 count " + count);
    }

    @Test
    void scopedModeHasNoPublicCount() {
        Ipv4Space scoped = new Ipv4Space(List.of(), List.of("127.0.0.1/32"), 1);
        assertEquals(-1, scoped.public24Count());
    }

    private static long ip(int a, int b, int c, int d) {
        return Ipv4Space.ipv4ToLong(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
    }

}
