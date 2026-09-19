package xyz.mcutils.backend.service.tracker;

import org.springframework.beans.factory.annotation.Value;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.color.ColorUtils;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.server.java.JavaVersion;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies hosts that passed a discovery connect probe: performs a full Java status ping on the
 * discovered port, harvests the player sample, then walks ports incrementally. The walk ceiling
 * is always {@code lastWorkingPort + window}, recomputed after every verified server, so gapped
 * multi-server hosts (25565, 25566, 25571, 25580) are fully discovered while non-MC services
 * like RCON occupying adjacent ports never extend the search.
 * <p>
 * Verified servers are handed to the {@link ServerTrackerSink} as a rich {@link ServerSnapshot}
 * (counts, version/protocol/platform, MOTD + fingerprints, latency, secure-chat flags, verdict
 * players) so the tracker store persists telemetry, not just a counter.
 * <p>
 * Not a Spring bean: {@link ServerTrackerService} constructs it (and its collaborators) after
 * the context is ready, so nothing here needs to be eagerly instantiable.
 */
public class ServerTrackerVerifier {

    private static final DNSRecord[] NO_RECORDS = new DNSRecord[0];

    /**
     * Full telemetry of one verified server, harvested from the status token plus the honeypot
     * verdict. {@code players} holds the post-verdict, post-identity sample (may be empty);
     * {@code honeypot} marks the server itself as flagged.
     */
    public record ServerSnapshot(
            String ip,
            int port,
            int online,
            int maxPlayers,
            String version,
            Integer protocol,
            String platform,
            String motd,
            byte[] motdHash,
            byte[] faviconHash,
            boolean modded,
            Integer latencyMs,
            boolean preventsChatReports,
            boolean enforcesSecureChat,
            boolean previewsChat,
            List<HoneypotDetector.SampleEntry> players,
            boolean honeypot
    ) {}

    /**
     * Persists a verified server snapshot. The production implementation writes to
     * {@code tracker_servers} + {@code tracker_player_history} + {@code tracker_server_online_history};
     * alternative implementations may collect snapshots.
     */
    public interface ServerTrackerSink {
        void recordServer(ServerSnapshot snapshot);
    }

    /**
     * Accepts harvested players for the submit queue. Returns the number actually enqueued.
     */
    public interface PlayerHarvester {
        int harvest(List<HoneypotDetector.SampleEntry> players);
    }

    /**
     * Result of verifying one host at its discovered port.
     *
     * @param serversFound    number of verified Java servers found (discovered port + walk)
     * @param playersEnqueued total players handed to the submit queue
     * @param honeypot        whether any verified server on the host was flagged
     */
    public record HostResult(int serversFound, int playersEnqueued, boolean honeypot) {}

    public static final HostResult NOT_A_SERVER = new HostResult(0, 0, false);

    private record ServerOutcome(int playersEnqueued, boolean honeypot) {}

    private record PingResult(JavaServerStatusToken token, Integer latencyMs) {}

    private final JavaMinecraftServerPinger pinger;
    private final HoneypotDetector honeypotDetector;
    private final ServerTrackerSink serverSink;
    private final PlayerHarvester harvester;
    private final PlayerSampleVerifier playerSampleVerifier;
    private final ServerTrackerMetric metrics; // nullable: metrics recording is optional
    private final int window;
    private final int maxPort;
    private final int probeCapPerIp;
    private final int verifyTimeoutMs;

    public ServerTrackerVerifier(
            JavaMinecraftServerPinger pinger,
            HoneypotDetector honeypotDetector,
            ServerTrackerSink serverSink,
            PlayerHarvester harvester,
            PlayerSampleVerifier playerSampleVerifier,
            ServerTrackerMetric metrics,
            @Value("${mc-utils.server-tracker.ports.window:10}") int window,
            @Value("${mc-utils.server-tracker.ports.max:65535}") int maxPort,
            @Value("${mc-utils.server-tracker.ports.probe-cap-per-ip:300}") int probeCapPerIp,
            @Value("${mc-utils.server-tracker.verify.timeout-ms:5000}") int verifyTimeoutMs
    ) {
        this.pinger = pinger;
        this.honeypotDetector = honeypotDetector;
        this.serverSink = serverSink;
        this.harvester = harvester;
        this.playerSampleVerifier = playerSampleVerifier;
        this.metrics = metrics;
        this.window = window;
        this.maxPort = maxPort;
        this.probeCapPerIp = probeCapPerIp;
        this.verifyTimeoutMs = verifyTimeoutMs;
    }

    /**
     * Verifies {@code ip} at {@code discoveredPort} (the port a discovery probe found open) and
     * walks up to {@code window} ports beyond the last working port. Empty result when the
     * discovered port does not answer a valid Java status ping.
     */
    public HostResult verifyHost(String ip, int discoveredPort) {
        PingResult base = ping(ip, discoveredPort);
        if (base == null) {
            return NOT_A_SERVER;
        }

        ServerOutcome baseOutcome = handleServer(ip, discoveredPort, base.token(), base.latencyMs());
        int serversFound = 1;
        int playersEnqueued = baseOutcome.playersEnqueued();

        // A honeypot is just a honeypot: flag it, record it, and skip the whole IP — no port
        // walk, nothing further to harvest.
        if (baseOutcome.honeypot()) {
            return new HostResult(serversFound, playersEnqueued, true);
        }

        // Sliding window: the ceiling is (last working port + window), recomputed per verified server.
        int lastWorking = discoveredPort;
        int probes = 1;
        for (int port = discoveredPort + 1; port <= Math.min(lastWorking + window, maxPort) && probes < probeCapPerIp; port++, probes++) {
            if (metrics != null) {
                metrics.recordWalkProbe();
            }
            PingResult walk = ping(ip, port);
            if (walk != null) {
                lastWorking = port;
                ServerOutcome walkOutcome = handleServer(ip, port, walk.token(), walk.latencyMs());
                serversFound++;
                playersEnqueued += walkOutcome.playersEnqueued();
                if (walkOutcome.honeypot()) {
                    // Flagged mid-walk: skip the remaining ports of this IP.
                    return new HostResult(serversFound, playersEnqueued, true);
                }
            }
        }
        return new HostResult(serversFound, playersEnqueued, false);
    }

    private ServerOutcome handleServer(String ip, int port, JavaServerStatusToken token, Integer latencyMs) {
        if (metrics != null) {
            metrics.recordServerVerified();
        }
        HoneypotDetector.Verdict verdict = evaluate(honeypotDetector, ip, port, token);
        for (String reason : verdict.dropReasons()) {
            if (metrics != null) {
                metrics.recordSampleDropped(reason);
            }
        }
        if (metrics != null) {
            if (verdict.honeypotServer()) {
                metrics.recordHoneypotServer();
                if (verdict.dropReasons().contains("farm_blocked")) {
                    metrics.recordHoneypotFingerprintBlocked();
                }
            }
            metrics.recordPlayersHarvested(verdict.players().size());
        }

        // The honeypot verdict sees the raw sample; identity verification then drops entries
        // whose (name, uuid) pair does not match Mojang before anything is persisted/enqueued.
        List<HoneypotDetector.SampleEntry> verifiedPlayers = playerSampleVerifier.verify(verdict.players());

        serverSink.recordServer(buildSnapshot(ip, port, token, latencyMs, verifiedPlayers, verdict.honeypotServer()));
        int enqueued = verifiedPlayers.isEmpty() ? 0 : harvester.harvest(verifiedPlayers);
        return new ServerOutcome(enqueued, verdict.honeypotServer());
    }

    /**
     * Runs the honeypot verdict over a status token's sample. Shared by the verify path and the
     * refresh cycle so both loops apply identical anti-poisoning.
     */
    public static HoneypotDetector.Verdict evaluate(HoneypotDetector detector, String ip, int port, JavaServerStatusToken token) {
        var players = token.getPlayers();
        List<HoneypotDetector.SampleEntry> sample = new ArrayList<>();
        if (players.sample() != null) {
            for (JavaServerStatusToken.Players.Sample entry : players.sample()) {
                sample.add(new HoneypotDetector.SampleEntry(entry.id(), entry.name()));
            }
        }
        return detector.evaluate(ip, port, players.online(), players.max(), sample);
    }

    public static ServerSnapshot buildSnapshot(String ip, int port, JavaServerStatusToken token, Integer latencyMs,
                                               List<HoneypotDetector.SampleEntry> players, boolean honeypot) {
        String versionName = token.getVersion().getName();
        String motd = motdText(token.getDescription());
        return new ServerSnapshot(
                ip,
                port,
                token.getPlayers().online(),
                token.getPlayers().max(),
                versionName,
                token.getVersion().getProtocol(),
                derivePlatform(versionName),
                motd,
                motd != null ? sha256(motd) : null,
                token.getFavicon() != null ? sha256(token.getFavicon()) : null,
                token.isModded(),
                latencyMs,
                token.isPreventsChatReports(),
                token.isEnforcesSecureChat(),
                token.isPreviewsChat(),
                List.copyOf(players),
                honeypot
        );
    }

    /**
     * Server-software string from the version name ("Paper 1.21.4" -> "Paper"), or null for a
     * plain version name. Mirrors {@link JavaVersion#detailedCopy()} semantics.
     */
    private static String derivePlatform(String versionName) {
        if (versionName == null || !versionName.contains(" ")) {
            return null;
        }
        String[] parts = versionName.split(" ");
        if (parts.length != 2) {
            return null;
        }
        String platform = ColorUtils.stripColor(parts[0]); // Strip legacy/modern color codes ("§4Paper" -> "Paper")
        return platform.isBlank() ? null : platform;
    }

    /**
     * MOTD as storage text: color-stripped for legacy strings, canonical JSON for component
     * objects ({@code null} when the server sends no description).
     */
    private static String motdText(Object description) {
        if (description == null) {
            return null;
        }
        if (description instanceof String legacy) {
            return ColorUtils.stripColor(legacy);
        }
        return Constants.GSON.toJson(description);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * @return the parsed token with round-trip latency, or null when the port does not answer
     * a valid Java status ping
     */
    private PingResult ping(String ip, int port) {
        long startNanos = System.nanoTime();
        try {
            JavaServerStatusToken token = pinger.pingToken(ip, ip, port, NO_RECORDS, verifyTimeoutMs);
            if (token == null || token.getVersion() == null || token.getPlayers() == null) {
                return null;
            }
            return new PingResult(token, (int) ((System.nanoTime() - startNanos) / 1_000_000L));
        } catch (RuntimeException e) { // refused, timed out, or a non-MC service replied garbage
            return null;
        }
    }
}