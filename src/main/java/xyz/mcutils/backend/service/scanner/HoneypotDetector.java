package xyz.mcutils.backend.service.scanner;

import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.UUIDUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Layered anti-honeypot filter for player samples harvested from server status pings.
 * <p>
 * Layers, cheapest first:
 * <ol>
 *   <li><b>Structural</b> — sample entries must carry a version-4 (online-mode) UUID and a
 *       sane Minecraft username; duplicates are dropped.</li>
 *   <li><b>Consistency</b> — a non-empty sample with {@code online <= 0}, or more sample entries
 *       than reported online players, is treated as fabricated: the harvest is dropped.</li>
 *   <li><b>Farm fingerprinting</b> — the canonical ordered set of UUIDs is fingerprinted; when
 *       the same fingerprint is seen across {@code maxDistinctNets} distinct /24 subnets within
 *       the TTL window it is a honeypot farm, and its players are dropped.</li>
 *   <li><b>Per-IP repetition</b> — the same fingerprint on {@code maxIdenticalPortSamples} or
 *       more ports of one host flags that host.</li>
 * </ol>
 * The final anti-poisoning gate is Mojang itself: every harvested UUID the submit pipeline
 * cannot resolve is silently discarded there.
 */
@Component
public class HoneypotDetector {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern COLOR_CODE = Pattern.compile("§.");

    private final ScanFingerprintStore store;
    private final int maxDistinctNets;
    private final Duration window;
    private final int maxIdenticalPortSamples;

    public HoneypotDetector(
            ScanFingerprintStore store,
            @Value("${mc-utils.server-scanner.honeypot.max-distinct-nets-per-fingerprint:50}") int maxDistinctNets,
            @Value("${mc-utils.server-scanner.honeypot.window-hours:24}") long windowHours,
            @Value("${mc-utils.server-scanner.honeypot.max-identical-port-samples:3}") int maxIdenticalPortSamples
    ) {
        this.store = store;
        this.maxDistinctNets = maxDistinctNets;
        this.window = Duration.ofHours(windowHours);
        this.maxIdenticalPortSamples = maxIdenticalPortSamples;
    }

    /**
     * A player entry as advertised by a server's status sample.
     */
    public record SampleEntry(UUID uuid, String name) {}

    /**
     * Outcome of evaluating one server's sample.
     *
     * @param serverOk       whether the server itself is accepted (a valid status response)
     * @param players        players that passed every filter and may be enqueued
     * @param honeypotServer whether this host/fingerprint was flagged as a honeypot
     * @param dropReasons    metric reasons for every dropped entry/whole-sample rejection
     */
    public record Verdict(boolean serverOk, List<SampleEntry> players, boolean honeypotServer, List<String> dropReasons) {}

    /**
     * Evaluates a server sample. The IP is the probing destination (hosts are scanned by raw IP).
     */
    public Verdict evaluate(String ip, int port, int online, int max, List<SampleEntry> sample) {
        List<String> dropReasons = new ArrayList<>();
        if (sample == null || sample.isEmpty()) {
            return new Verdict(true, List.of(), false, dropReasons);
        }

        // Whole-sample consistency checks: fabricated samples are dropped entirely.
        boolean consistent = online > 0 && sample.size() <= online && online <= max;
        List<SampleEntry> kept = new ArrayList<>(sample.size());
        if (consistent) {
            Set<UUID> seen = new HashSet<>();
            for (SampleEntry entry : sample) {
                if (entry.uuid() == null || !UUIDUtils.isOnlineMode(entry.uuid())) {
                    dropReasons.add("non_v4");
                }
                else if (!isValidName(entry.name())) {
                    dropReasons.add("bad_name");
                }
                else if (!seen.add(entry.uuid())) {
                    dropReasons.add("duplicate");
                }
                else {
                    kept.add(entry);
                }
            }
        }
        else {
            dropReasons.add("consistency");
        }

        if (kept.isEmpty()) {
            return new Verdict(true, List.of(), false, dropReasons);
        }

        // Farm detection: one fingerprint observed from too many distinct /24 subnets.
        String fingerprint = fingerprint(kept);
        String net24 = net24(ip);
        long distinctNets = store.recordFingerprint(fingerprint, net24, window);
        if (distinctNets >= maxDistinctNets) {
            dropReasons.add("farm_blocked");
            return new Verdict(true, List.of(), true, dropReasons);
        }

        // Per-IP repetition: the same sample on many ports of one host.
        long distinctPorts = store.recordIpPortFingerprint(ip, port, fingerprint, window);
        if (distinctPorts >= maxIdenticalPortSamples) {
            dropReasons.add("ip_repeat");
            return new Verdict(true, List.of(), true, dropReasons);
        }

        return new Verdict(true, kept, false, dropReasons);
    }

    static boolean isValidName(String rawName) {
        if (rawName == null) {
            return false;
        }
        return NAME_PATTERN.matcher(COLOR_CODE.matcher(rawName).replaceAll("")).matches();
    }

    /**
     * Canonical fingerprint of a sample: SHA-256 over the sorted, dashless UUIDs.
     */
    static String fingerprint(List<SampleEntry> entries) {
        List<String> uuids = entries.stream()
                .map(e -> e.uuid().toString().replace("-", ""))
                .sorted(Comparator.naturalOrder())
                .toList();
        return Hashing.sha256().hashString(String.join(",", uuids), StandardCharsets.UTF_8).toString();
    }

    /**
     * The /24 prefix ("a.b.c") of a dotted-quad IPv4 string.
     */
    static String net24(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? ip : ip.substring(0, lastDot);
    }
}