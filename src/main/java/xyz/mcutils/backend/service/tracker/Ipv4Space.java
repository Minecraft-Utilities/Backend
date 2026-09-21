package xyz.mcutils.backend.service.tracker;

import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Endless, random-order sweep of the public IPv4 space at /24 granularity.
 * <p>
 * A <em>cycle</em> shuffles the 65536 /16 prefixes with a seeded SplitMix64 PRNG (never
 * materializing the 2^24-entry /24 permutation) and shuffles the 256 /24s of each /16 as it enters
 * it, so the /24s arrive in a random order and every public /24 is yielded exactly once per cycle.
 * Finishing a cycle rolls straight into the next one with a fresh shuffle, so {@link #next24()}
 * never runs out: the sweep loops over the whole space forever and nothing is persisted between
 * cycles or restarts.
 * <p>
 * All IANA special-purpose ranges (RFC 6890), the US DoD allocations (the "government" ranges),
 * and any operator-supplied extra CIDRs are skipped. A non-empty {@code includeCidrs} set puts
 * the iterator into <em>scoped mode</em>: only the /24s overlapping those CIDRs are yielded
 * (exclusions ignored), in order, cycling through them forever — which lets an operator (or CI)
 * scan a bounded network.
 * <p>
 * Cycle {@code n} is seeded with {@code mix(rootSeed, n)}, so one root seed reproduces the same
 * sequence of orders.
 */
public final class Ipv4Space {

    /**
     * IPv4 as an unsigned 32-bit value stored in a long.
     */
    private static final long IPV4_MASK = 0xFFFFFFFFL;

    /**
     * All ranges that must never be probed: IANA special-purpose + US DoD allocations.
     * Parsed once in the static initializer.
     */
    public static final List<Cidr> DEFAULT_EXCLUSIONS = parseCidrs(List.of(
            // IANA special-purpose (RFC 6890/5735)
            "0.0.0.0/8",            // "this" network
            "10.0.0.0/8",           // private
            "25.0.0.0/8",           // reserved (UK Ministry of Defence)
            "100.64.0.0/10",        // carrier-grade NAT
            "127.0.0.0/8",          // loopback
            "169.254.0.0/16",       // link-local
            "172.16.0.0/12",        // private
            "192.0.0.0/24",         // IETF protocol assignments
            "192.0.2.0/24",         // TEST-NET-1
            "192.88.99.0/24",       // 6to4 relay anycast
            "192.168.0.0/16",       // private
            "198.18.0.0/15",        // benchmarking
            "198.51.100.0/24",      // TEST-NET-2
            "203.0.113.0/24",       // TEST-NET-3
            "224.0.0.0/4",          // multicast
            "240.0.0.0/4",          // reserved (incl. 255.255.255.255)
            // US DoD allocations (ARIN): the "government" ranges
            "6.0.0.0/8",
            "11.0.0.0/8",
            "21.0.0.0/8",
            "22.0.0.0/8",
            "26.0.0.0/8",
            "30.0.0.0/8",
            "55.0.0.0/8",
            "214.0.0.0/8",
            "215.0.0.0/8"
    ));

    /**
     * An inclusive IPv4 range stored as unsigned 32-bit values in longs.
     */
    public record Cidr(long start, long endInclusive) {
        public boolean contains(long address) {
            return address >= start && address <= endInclusive;
        }
    }

    private final List<Cidr> exclusions;
    private final List<Cidr> includes;
    private final long rootSeed;

    // Cycle state
    private long cycleSeed;
    private long cycle;
    private long cycle24s;
    private long total24s;
    private int[] permuted16s = new int[0];
    private int current16Pos;
    private int current24Index;
    private int[] current16Shuffle = new int[0];
    private boolean[] current16Excluded = new boolean[0];

    // Scoped-mode state
    private int includeIndex;
    private long includeBase;
    private long includeEnd;

    /**
     * @param excludeCidrs extra CIDRs to exclude on top of {@link #DEFAULT_EXCLUSIONS}
     * @param includeCidrs when non-empty, restrict scanning to these CIDRs (scoped mode)
     * @param rootSeed     root seed for the cycle shuffles; same seed → same sequence of orders
     */
    public Ipv4Space(@Nullable List<String> excludeCidrs, @Nullable List<String> includeCidrs, long rootSeed) {
        this.rootSeed = rootSeed;
        this.includes = includeCidrs == null || includeCidrs.isEmpty()
                ? List.of()
                : parseCidrs(includeCidrs);
        List<Cidr> merged = new ArrayList<>(DEFAULT_EXCLUSIONS);
        if (excludeCidrs != null) {
            merged.addAll(parseCidrs(excludeCidrs));
        }
        this.exclusions = List.copyOf(merged);
        this.cycleSeed = mix(rootSeed, 0);
        if (hasIncludeScope()) { // scoped mode: cycle through the include cidrs
            this.cycle = 1;
            enterInclude(0);
        } else if (public24Count() <= 0) {
            throw new IllegalArgumentException("IPv4 exclusions cover the whole space: nothing left to scan");
        } else {
            startCycle();
        }
    }

    public boolean hasIncludeScope() {
        return !includes.isEmpty();
    }

    /**
     * Approximate number of /24 subnets swept per cycle (full mode only), used for ETA logging:
     * total /24s minus those covered by the exclusion list. Exclusions are disjoint, so overlap is
     * not a concern; operator extras count toward the sum.
     *
     * @return public /24 count, or -1 in scoped mode where the space has no fixed size
     */
    public long public24Count() {
        if (hasIncludeScope()) {
            return -1;
        }
        long excluded = 0;
        for (Cidr cidr : exclusions) {
            excluded += ((cidr.endInclusive() - cidr.start()) >> 8) + 1;
        }
        return (1L << 24) - excluded;
    }

    /**
     * @return the root seed, for logging/reproduction of the sweep order
     */
    public long rootSeed() {
        return rootSeed;
    }

    /**
     * @return seed of the shuffle in progress; also seeds the host order inside each /24
     */
    public long cycleSeed() {
        return cycleSeed;
    }

    /**
     * @return 1-based number of the sweep in progress; cycles have no end
     */
    public long cycle() {
        return cycle;
    }

    /**
     * @return /24s yielded since the current cycle started
     */
    public long cycle24s() {
        return cycle24s;
    }

    /**
     * @return /24s yielded since this instance was created (monotonic across cycle rollovers)
     */
    public long total24s() {
        return total24s;
    }

    /**
     * @return the next /24 network address as an unsigned 32-bit long; never fails, because a
     *         finished cycle rolls straight into the next one
     */
    public long next24() {
        if (hasIncludeScope()) {
            return next24Scoped();
        }
        while (true) {
            while (current16Pos < 65536) {
                while (current24Index < 256) {
                    long base = ((long) current16Shuffle[current24Index] & 0xFFL) << 8;
                    this.current24Index++;
                    if (!current16Excluded[current24Index - 1]) {
                        this.cycle24s++;
                        this.total24s++;
                        return (((long) permuted16s[current16Pos] & 0xFFFFL) << 16) | base;
                    }
                }
                this.current16Pos++;
                if (current16Pos < 65536) {
                    enter16(permuted16s[current16Pos]);
                }
            }
            startCycle(); // the sweep is over: shuffle again and keep going
        }
    }

    private long next24Scoped() {
        while (true) {
            if (includeBase <= includeEnd) {
                long base = includeBase;
                includeBase += 256;
                this.cycle24s++;
                this.total24s++;
                return base;
            }
            if (++includeIndex < includes.size()) {
                enterInclude(includeIndex);
            } else { // wrapped: another pass over the include ranges
                this.cycle++;
                this.cycle24s = 0;
                includeIndex = 0;
                enterInclude(0);
            }
        }
    }

    /**
     * Starts the next sweep with a shuffle derived from the root seed and the cycle number, so
     * consecutive cycles never walk the space in the same order.
     */
    private void startCycle() {
        this.cycleSeed = mix(rootSeed, cycle);
        this.permuted16s = shuffledIndexes(cycleSeed, 65536);
        this.current16Pos = 0;
        this.cycle24s = 0;
        this.cycle++;
        enter16(permuted16s[0]);
    }

    /**
     * Whether the /24 containing {@code address} is excluded.
     */
    public boolean isExcluded(long address) {
        long base = address & ~0xFFL;
        for (Cidr cidr : exclusions) {
            if (cidr.contains(base)) {
                return true;
            }
        }
        return false;
    }

    private void enterInclude(int index) {
        Cidr cidr = includes.get(index);
        this.includeBase = cidr.start() & ~0xFFL;
        this.includeEnd = cidr.endInclusive() & ~0xFFL;
    }

    private void enter16(int prefix16) {
        current16Shuffle = shuffledIndexes(mix(cycleSeed, prefix16), 256);
        current16Excluded = new boolean[256];
        long network = ((long) prefix16 & 0xFFFFL) << 16;
        for (int i = 0; i < 256; i++) {
            long base = network | (((long) current16Shuffle[i] & 0xFFL) << 8);
            current16Excluded[i] = isExcluded(base);
        }
        current24Index = 0;
    }

    /**
     * Parses CIDR strings ("a.b.c.d/n") into ranges.
     */
    public static List<Cidr> parseCidrs(List<String> cidrs) {
        return cidrs.stream().map(Ipv4Space::parseCidr).toList();
    }

    public static Cidr parseCidr(String cidr) {
        String[] parts = cidr.split("/", 2);
        int prefix = Integer.parseInt(parts[1]);
        long address;
        try {
            address = ipv4ToLong(InetAddress.getByName(parts[0]).getAddress());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid CIDR address '%s'".formatted(parts[0]), e);
        }
        long mask = prefix == 0 ? 0 : IPV4_MASK << (32 - prefix);
        long start = address & mask;
        long end = start | (~mask & IPV4_MASK);
        return new Cidr(start, end);
    }

    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /**
     * Deterministic Fisher-Yates shuffle of {@code [0, size)} using a SplitMix64 PRNG seeded
     * with {@code seedValue}. Identical across runs and JDKs by construction.
     */
    public static int[] shuffledIndexes(long seedValue, int size) {
        long[] state = { mix(seedValue, 0x9E3779B97F4A7C15L) };
        int[] indexes = new int[size];
        for (int i = 0; i < size; i++) {
            indexes[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            long next = splitmix64(state[0]);
            state[0] = next;
            int j = (int) (Long.remainderUnsigned(next, i + 1L));
            int tmp = indexes[i];
            indexes[i] = indexes[j];
            indexes[j] = tmp;
        }
        return indexes;
    }

    private static long splitmix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /**
     * Converts a raw 4-byte IPv4 address to an unsigned 32-bit long.
     */
    public static long ipv4ToLong(byte[] address) {
        return ((address[0] & 0xFFL) << 24)
                | ((address[1] & 0xFFL) << 16)
                | ((address[2] & 0xFFL) << 8)
                | (address[3] & 0xFFL);
    }

    /**
     * Formats an unsigned 32-bit long as a dotted-quad string.
     */
    public static String longToIpv4(long address) {
        return "%d.%d.%d.%d".formatted(
                (address >>> 24) & 0xFFL,
                (address >>> 16) & 0xFFL,
                (address >>> 8) & 0xFFL,
                address & 0xFFL
        );
    }

    /**
     * The hosts within a /24 to probe: 1..254 (network and broadcast addresses skipped),
     * in a deterministic seeded order.
     */
    public static long[] hostsIn24(long base24, long seed) {
        long[] hosts = new long[254];
        for (int i = 0; i < 254; i++) {
            hosts[i] = base24 | (i + 1);
        }
        int[] order = shuffledIndexes(mix(seed, base24), 254);
        long[] permuted = new long[254];
        for (int i = 0; i < 254; i++) {
            permuted[i] = hosts[order[i]];
        }
        return permuted;
    }
}
