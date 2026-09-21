package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * The whole point of the sweep: one cycle covers every public /24 exactly once, then the space
     * reshuffles and starts over instead of running out.
     */
    @Test
    void sweepsEveryPublicSubnetExactlyOncePerCycle() {
        Ipv4Space space = space(2026);
        long perCycle = space.public24Count();
        long[] firstOrder = new long[8];
        BitSet seen = new BitSet(1 << 24);
        for (long i = 0; i < perCycle; i++) {
            long base = space.next24();
            assertFalse(seen.get((int) (base >>> 8)), "duplicate /24 in one cycle: " + Ipv4Space.longToIpv4(base));
            seen.set((int) (base >>> 8));
            assertFalse(space.isExcluded(base), "yielded excluded subnet " + Ipv4Space.longToIpv4(base));
            if (i < firstOrder.length) {
                firstOrder[(int) i] = base;
            }
        }
        assertEquals(1, space.cycle(), "a cycle ends after every public /24 has been swept");
        assertEquals(perCycle, space.cycle24s());
        assertEquals(perCycle, space.total24s());

        long[] nextOrder = new long[firstOrder.length];
        for (int i = 0; i < nextOrder.length; i++) {
            nextOrder[i] = space.next24();
        }
        assertEquals(2, space.cycle(), "the sweep rolls into the next cycle");
        assertEquals(nextOrder.length, space.cycle24s());
        assertFalse(Arrays.equals(firstOrder, nextOrder), "each cycle walks the space in a new order");
    }

    @Test
    void scopedModeCyclesThroughIncludedCidr() {
        Ipv4Space test = new Ipv4Space(List.of(), List.of("127.0.0.1/32"), 1);
        assertTrue(test.hasIncludeScope());
        assertEquals(1, test.cycle());
        assertEquals(ip(127, 0, 0, 0), test.next24());
        assertEquals(ip(127, 0, 0, 0), test.next24(), "the include ranges are re-swept forever");
        assertEquals(2, test.cycle());
    }

    @Test
    void exclusionsCoveringTheWholeSpaceAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Ipv4Space(
                List.of("0.0.0.0/1", "128.0.0.0/2", "192.0.0.0/3", "224.0.0.0/4", "240.0.0.0/4"),
                List.of(), 1
        ));
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
