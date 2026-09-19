package xyz.mcutils.backend.service.scanner;

import org.springframework.beans.factory.annotation.Value;
import xyz.mcutils.backend.metric.impl.scanner.ServerScannerMetric;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies hosts that passed a discovery connect probe: performs a full Java status ping on the
 * discovered port, harvests the player sample, then walks ports incrementally. The walk ceiling
 * is always {@code lastWorkingPort + window}, recomputed after every verified server, so gapped
 * multi-server hosts (25565, 25566, 25571, 25580) are fully discovered while non-MC services
 * like RCON occupying adjacent ports never extend the search.
 * <p>
 * Not a Spring bean: {@link ServerScannerService} constructs it (and its collaborators) after
 * the context is ready, so nothing here needs to be eagerly instantiable.
 */
public class ServerScanVerifier {

    private static final DNSRecord[] NO_RECORDS = new DNSRecord[0];

    /**
     * Persists a discovered server. The production implementation writes to
     * {@code scanned_servers}; alternative implementations may collect records.
     */
    public interface ScannedServerSink {
        void recordServer(String ip, int port, int sampleCount, boolean honeypot);
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
     * @param serversFound   number of verified Java servers found (discovered port + walk)
     * @param playersEnqueued total players handed to the submit queue
     * @param honeypot       whether any verified server on the host was flagged
     */
    public record HostResult(int serversFound, int playersEnqueued, boolean honeypot) {}

    public static final HostResult NOT_A_SERVER = new HostResult(0, 0, false);

    private record ServerOutcome(int playersEnqueued, boolean honeypot) {}

    private final JavaMinecraftServerPinger pinger;
    private final HoneypotDetector honeypotDetector;
    private final ScannedServerSink serverSink;
    private final PlayerHarvester harvester;
    private final ServerScannerMetric metrics; // nullable: metrics recording is optional
    private final int window;
    private final int maxPort;
    private final int probeCapPerIp;
    private final int verifyTimeoutMs;

    public ServerScanVerifier(
            JavaMinecraftServerPinger pinger,
            HoneypotDetector honeypotDetector,
            ScannedServerSink serverSink,
            PlayerHarvester harvester,
            ServerScannerMetric metrics,
            @Value("${mc-utils.server-scanner.ports.window:10}") int window,
            @Value("${mc-utils.server-scanner.ports.max:65535}") int maxPort,
            @Value("${mc-utils.server-scanner.ports.probe-cap-per-ip:500}") int probeCapPerIp,
            @Value("${mc-utils.server-scanner.verify.timeout-ms:5000}") int verifyTimeoutMs
    ) {
        this.pinger = pinger;
        this.honeypotDetector = honeypotDetector;
        this.serverSink = serverSink;
        this.harvester = harvester;
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
        JavaServerStatusToken token = ping(ip, discoveredPort);
        if (token == null) {
            return NOT_A_SERVER;
        }

        ServerOutcome base = handleServer(ip, discoveredPort, token);
        int serversFound = 1;
        int playersEnqueued = base.playersEnqueued();
        boolean honeypot = base.honeypot();

        // Sliding window: the ceiling is (last working port + window), recomputed per verified server.
        int lastWorking = discoveredPort;
        int probes = 1;
        int port = discoveredPort + 1;
        while (port <= Math.min(lastWorking + window, maxPort) && probes < probeCapPerIp) {
            if (metrics != null) {
                metrics.recordWalkProbe();
            }
            JavaServerStatusToken walkToken = ping(ip, port);
            if (walkToken != null) {
                lastWorking = port;
                ServerOutcome outcome = handleServer(ip, port, walkToken);
                serversFound++;
                playersEnqueued += outcome.playersEnqueued();
                honeypot |= outcome.honeypot();
            }
            port++;
            probes++;
        }
        return new HostResult(serversFound, playersEnqueued, honeypot);
    }

    private ServerOutcome handleServer(String ip, int port, JavaServerStatusToken token) {
        if (metrics != null) {
            metrics.recordServerVerified();
        }
        var players = token.getPlayers();
        List<HoneypotDetector.SampleEntry> sample = new ArrayList<>();
        if (players.sample() != null) {
            for (JavaServerStatusToken.Players.Sample entry : players.sample()) {
                sample.add(new HoneypotDetector.SampleEntry(entry.id(), entry.name()));
            }
        }

        HoneypotDetector.Verdict verdict = honeypotDetector.evaluate(ip, port, players.online(), players.max(), sample);
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

        serverSink.recordServer(ip, port, verdict.players().size(), verdict.honeypotServer());
        int enqueued = verdict.players().isEmpty() ? 0 : harvester.harvest(verdict.players());
        return new ServerOutcome(enqueued, verdict.honeypotServer());
    }

    /**
     * @return the parsed token when the port answers a valid Java status ping, else null
     */
    private JavaServerStatusToken ping(String ip, int port) {
        try {
            JavaServerStatusToken token = pinger.pingToken(ip, ip, port, NO_RECORDS, verifyTimeoutMs);
            return token != null && token.getVersion() != null && token.getPlayers() != null ? token : null;
        } catch (RuntimeException e) { // refused, timed out, or a non-MC service replied garbage
            return null;
        }
    }
}