package xyz.mcutils.backend.service.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import xyz.mcutils.backend.metric.impl.scanner.ServerScannerMetric;

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
 * Discovery stage of the internet scanner: asynchronous TCP connect probes across the whole
 * IPv4 space, using {@code java.nio} non-blocking channels with a single selector thread.
 * Every host is probed on each configured discovery port (e.g. 25564, 25565, 25566), so servers
 * running only on non-standard ports are found too. Hosts are probed in a seeded random order
 * (per /16, per /24, per host), one probe per port, no retries; the in-flight cap doubles as the
 * pacing mechanism.
 * <p>
 * CPU-conscious by design: the pending set is tracked only by an integer counter plus the
 * per-key attachment (no side map, no boxed deadlines), connects that complete synchronously
 * are finished immediately without a selector round-trip, and the expiry sweep walks the
 * selector's key set at most once per second.
 * <p>
 * Only the configured discovery ports are probed here — the port walk happens in
 * {@link ServerScanVerifier} on hosts where a discovery port verified. Open ports are handed to
 * the verifier via the {@link OpenPortHandler} callback (invoked on the selector thread;
 * dispatch to a pool belongs to the caller).
 * <p>
 * Not a Spring bean: {@link ServerScannerService} constructs it (and its collaborators) after
 * the context is ready, so nothing here needs to be eagerly instantiable.
 */
@Slf4j
public class ServerDiscoveryScanner {

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
    private static final Duration PENDING_SWEEP_INTERVAL = Duration.ofSeconds(1);

    private final Ipv4Space space;
    private final List<Integer> discoveryPorts;
    private final int concurrency;
    private final Duration connectTimeout;
    private final OpenPortHandler openPortHandler;
    private final ServerScannerMetric metrics; // nullable: metrics recording is optional
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger pendingCount = new AtomicInteger();

    // Selector-loop state
    private Selector selector;
    private long lastSweep;

    // Iteration state
    private long current24 = -1;
    private long[] currentHosts = new long[0];
    private int hostIndex;
    private boolean spaceExhausted;
    private volatile Ipv4Space.Progress cursor;
    private volatile long completed24s;

    public ServerDiscoveryScanner(
            Ipv4Space space,
            OpenPortHandler openPortHandler,
            ServerScannerMetric metrics,
            @Value("${mc-utils.server-scanner.ports.discovery:25564,25565,25566,25567}") String discoveryPortsCsv,
            @Value("${mc-utils.server-scanner.discovery.concurrency:50000}") int concurrency,
            @Value("${mc-utils.server-scanner.discovery.connect-timeout-ms:1000}") long connectTimeoutMs
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

    public Ipv4Space.Progress cursor() {
        return cursor;
    }

    public long completed24s() {
        return completed24s;
    }

    /**
     * @return true once the entire space has been iterated (the loop may still be draining
     *         in-flight connections)
     */
    public boolean isComplete() {
        return spaceExhausted;
    }

    /**
     * Runs the discovery loop until the space is exhausted or {@link #close()} is called.
     */
    public void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Discovery scanner is already running");
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
                if (exhaustedAndIdle()) {
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Discovery scanner selector failed", e);
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
            long ip = nextHost();
            if (ip < 0) {
                return; // space exhausted
            }
            String ipString = Ipv4Space.longToIpv4(ip);
            for (int port : discoveryPorts) {
                if (metrics != null) {
                    metrics.recordProbe();
                }
                try {
                    SocketChannel channel = SocketChannel.open();
                    channel.configureBlocking(false);
                    channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    if (channel.connect(new InetSocketAddress(ipString, port))) {
                        // Connected synchronously: finish immediately, no selector round-trip.
                        try {
                            if (metrics != null) {
                                metrics.recordConnectOpen();
                            }
                            openPortHandler.handle(ipString, port);
                        } finally {
                            channel.close();
                        }
                    } else {
                        channel.register(selector, SelectionKey.OP_CONNECT,
                                new Pending(ipString, port, System.nanoTime() + connectTimeout.toNanos()));
                        pendingCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    // Unresolvable/unroutable (e.g. no route): the probe already counted, move on.
                }
            }
        }
    }

    private void sweepExpired() {
        long now = System.nanoTime();
        if (now - lastSweep < PENDING_SWEEP_INTERVAL.toNanos()) {
            return;
        }
        lastSweep = now;
        // No side map: deadlines live in each key's attachment, and the selector owns the set.
        // Collect first, then abandon, so selector.keys() is never mutated mid-iteration.
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

    private boolean exhaustedAndIdle() {
        return spaceExhausted && pendingCount.get() == 0;
    }

    /**
     * @return the next host to probe as an unsigned 32-bit long, or -1 when the space is exhausted
     */
    private long nextHost() {
        while (true) {
            if (current24 >= 0 && hostIndex < currentHosts.length) {
                return currentHosts[hostIndex++];
            }
            if (current24 >= 0) { // finished this /24
                long completed = ++completed24s;
                this.cursor = space.progress();
                if (metrics != null) {
                    ServerScannerMetric.updateProgress(completed);
                }
                current24 = -1;
            }
            long next24 = space.next24();
            if (next24 < 0) {
                spaceExhausted = true;
                return -1;
            }
            current24 = next24;
            currentHosts = Ipv4Space.hostsIn24(current24, space.seed());
            hostIndex = 0;
        }
    }

    private static List<Integer> parsePorts(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("mc-utils.server-scanner.ports.discovery must not be empty");
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