package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Discovery stage of the internet tracker: asynchronous TCP connect probes across the whole
 * IPv4 space, using {@code java.nio} non-blocking channels with a single selector thread.
 * Every host is probed on each configured discovery port (e.g. 25564, 25565, 25566), so servers
 * running only on non-standard ports are found too. Hosts are probed in a seeded random order
 * (per cycle, per /16, per /24, per host), one probe per port, no retries; the in-flight cap
 * doubles as the pacing mechanism.
 * <p>
 * The loop only ever ends on {@link #close()}: {@link Ipv4Space#next24()} never exhausts, it rolls
 * into the next shuffled sweep of the whole IPv4 space, so discovery runs for the lifetime of the
 * process.
 * <p>
 * CPU-conscious by design: the pending set is tracked only by an integer counter plus the
 * per-key attachment (no side map, no boxed deadlines), connects that complete synchronously
 * are finished immediately without a selector round-trip, and the expiry sweep walks the
 * selector's key set at most once per quarter second (aligned with the connect timeout, so
 * dead sockets are abandoned near their true deadline instead of lingering for a full second
 * and inflating the in-flight set / FD usage).
 * <p>
 * Only the configured discovery ports are probed here — the port walk happens in
 * {@link ServerTrackerVerifier} on hosts where a discovery port verified. Open ports are handed to
 * the verifier via the {@link OpenPortHandler} callback (invoked on the selector thread;
 * dispatch to a pool belongs to the caller).
 * <p>
 * Not a Spring bean: {@link ServerTrackerService} constructs it (and its collaborators) after
 * the context is ready, so nothing here needs to be eagerly instantiable.
 */
@Slf4j
public class ServerTrackerDiscovery {

    /**
     * Receives an IP + port whose TCP connect succeeded. Called from the selector thread.
     */
    public interface OpenPortHandler {
        void handle(String ip, int port);
    }

    /**
     * Per-connection attachment: the target plus the connect deadline (nanoTime).
     */
    private record Pending(String ip, int port, long deadline) {}

    private static final int SELECT_TIMEOUT_MS = 100;
    /**
     * How often the expiry sweep scans the registered key set. Aligned to the connect timeout:
     * a pending socket is abandoned within one connect-timeout of its deadline, while the scan
     * cost stays O(keys) per interval regardless of concurrency — a much faster cadence would
     * spend all its time rescanning the in-flight set and starve {@code fillPending()}.
     */
    private static final Duration SWEEP_INTERVAL = Duration.ofMillis(250);

    private final Ipv4Space space;
    private final List<Integer> discoveryPorts;
    private final int concurrency;
    private final Duration connectTimeout;
    private final OpenPortHandler openPortHandler;
    private final ServerTrackerMetric metrics; // nullable: metrics recording is optional
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger pendingCount = new AtomicInteger();

    // Selector-loop state
    private Selector selector;
    private long lastSweep;

    // Iteration state
    private long current24 = -1;
    private long[] currentHosts = new long[0];
    private int hostIndex;

    public ServerTrackerDiscovery(
            Ipv4Space space,
            OpenPortHandler openPortHandler,
            ServerTrackerMetric metrics,
            @Value("${mc-utils.server-tracker.ports.discovery:25564,25565,25566,25567}") String discoveryPortsCsv,
            @Value("${mc-utils.server-tracker.discovery.concurrency:20000}") int concurrency,
            @Value("${mc-utils.server-tracker.discovery.connect-timeout-ms:1000}") long connectTimeoutMs
    ) {
        this.space = space;
        this.openPortHandler = openPortHandler;
        this.metrics = metrics;
        this.discoveryPorts = parsePorts(discoveryPortsCsv);
        this.concurrency = concurrency;
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
    }

    public int discoveryPortCount() {
        return discoveryPorts.size();
    }

    /**
     * Runs the discovery loop until {@link #close()} is called: the space never exhausts, so the
     * sweep restarts from a fresh shuffled order every time it has walked all of IPv4.
     */
    public void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Discovery stage is already running");
        }
        try {
            selector = Selector.open();
            lastSweep = System.nanoTime();
            while (running.get()) {
                fillPending();
                selector.select(SELECT_TIMEOUT_MS);
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        abandon(key);
                        continue;
                    }
                    SocketChannel channel = (SocketChannel) key.channel();
                    try {
                        if (channel.finishConnect()) {
                            pendingCount.decrementAndGet();
                            if (metrics != null) {
                                metrics.recordConnectOpen();
                            }
                            Pending pending = (Pending) key.attachment();
                            openPortHandler.handle(pending.ip(), pending.port());
                        } else {
                            abandon(key);
                            continue;
                        }
                    } catch (IOException e) {
                        abandon(key);
                        continue;
                    }
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                    }
                }
                sweepExpired();
            }
        } catch (IOException e) {
            log.error("Discovery stage selector failed", e);
        } finally {
            closeAll();
            running.set(false);
        }
    }

    /**
     * Requests the loop to stop; the loop closes all pending channels itself.
     */
    public void close() {
        running.set(false);
        if (selector != null) {
            selector.wakeup();
        }
    }

    private void fillPending() {
        while (running.get() && pendingCount.get() < concurrency) {
            String ipString = Ipv4Space.longToIpv4(nextHost());
            for (int port : discoveryPorts) {
                if (metrics != null) {
                    metrics.recordProbe();
                }
                // Deliberately NOT try-with-resources: the channel is owned by the selector once
                // registered, so it must stay open until the selector finishes/abandons it. Only
                // the synchronous-success and immediate-failure paths close here.
                SocketChannel channel = null;
                try {
                    channel = SocketChannel.open();
                    channel.configureBlocking(false);
                    channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    if (channel.connect(new InetSocketAddress(ipString, port))) {
                        // Connected synchronously: finish immediately, no selector round-trip.
                        if (metrics != null) {
                            metrics.recordConnectOpen();
                        }
                        openPortHandler.handle(ipString, port);
                        channel.close();
                    } else {
                        channel.register(selector, SelectionKey.OP_CONNECT,
                                new Pending(ipString, port, System.nanoTime() + connectTimeout.toNanos()));
                        pendingCount.incrementAndGet();
                        channel = null; // ownership handed to the selector
                    }
                } catch (IOException e) {
                    // Connect failed (unresolvable/unroutable/RST) or register failed: close the
                    // channel if we still own it, so no socket FD leaks on hot paths.
                    if (channel != null) {
                        try {
                            channel.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void sweepExpired() {
        long now = System.nanoTime();
        if (now - lastSweep < SWEEP_INTERVAL.toNanos()) {
            return;
        }
        lastSweep = now;
        // No side map: deadlines live in each key's attachment, and the selector owns the set.
        // Collect first, then abandon, so selector.keys() is never mutated mid-iteration.
        // The sweep scans the key set even when nothing is expired, so a pending socket that
        // fails to complete is abandoned within one connect-timeout of its deadline; the
        // pendingCount then drops and fillPending() replenishes immediately.
        List<SelectionKey> expired = new ArrayList<>();
        for (SelectionKey key : selector.keys()) {
            if (key.attachment() instanceof Pending pending && now >= pending.deadline()) {
                expired.add(key);
            }
        }
        for (SelectionKey key : expired) {
            abandon(key);
        }
    }

    private void abandon(SelectionKey key) {
        pendingCount.decrementAndGet();
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
    }

    private void closeAll() {
        if (selector == null) {
            return;
        }
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException ignored) {
            }
        }
        pendingCount.set(0);
        try {
            selector.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * @return the next host to probe as an unsigned 32-bit long; never runs out, because the
     *         space rolls into its next sweep instead of ending
     */
    private long nextHost() {
        if (current24 >= 0 && hostIndex < currentHosts.length) {
            return currentHosts[hostIndex++];
        }
        if (current24 >= 0) { // finished this /24
            current24 = -1;
            if (metrics != null) {
                ServerTrackerMetric.updateProgress(space.cycle24s());
            }
        }
        current24 = space.next24();
        currentHosts = Ipv4Space.hostsIn24(current24, space.cycleSeed());
        hostIndex = 0;
        return currentHosts[hostIndex++];
    }

    private static List<Integer> parsePorts(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("mc-utils.server-tracker.ports.discovery must not be empty");
        }
        List<Integer> ports = new ArrayList<>();
        for (String part : csv.split(",")) {
            int port = Integer.parseInt(part.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid discovery port: " + port);
            }
            if (!ports.contains(port)) {
                ports.add(port);
            }
        }
        return List.copyOf(ports);
    }
}