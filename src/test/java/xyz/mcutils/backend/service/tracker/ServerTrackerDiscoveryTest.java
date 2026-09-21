package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTrackerDiscoveryTest {

    /** How long a connect may take to count as dropped by the kernel rather than refused. */
    private static final int DROPPED_CONNECT_TIMEOUT_MS = 300;

    /**
     * The discovery loop must survive running out of IPs: the scoped space here wraps after a
     * single /24, so the loop is still probing the loopback listener several cycles later and only
     * stops when it is closed.
     */
    @Test
    void keepsProbingAcrossCyclesUntilClosed() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Ipv4Space space = new Ipv4Space(List.of(), List.of("127.0.0.1/32"), 1);
            AtomicLong openPortsReported = new AtomicLong();
            ServerTrackerDiscovery discovery = new ServerTrackerDiscovery(
                    space, (ip, port) -> openPortsReported.incrementAndGet(), null,
                    Integer.toString(listener.getLocalPort()), 512, 200
            );
            Thread thread = Thread.ofPlatform().daemon(true).name("discovery-test").start(discovery::run);
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while ((space.cycle() < 3 || openPortsReported.get() == 0) && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
                assertTrue(openPortsReported.get() > 0, "no probe reached the open port");
                assertTrue(space.cycle() >= 3, "the sweep stopped after " + space.cycle() + " cycle(s)");
                assertTrue(thread.isAlive(), "the discovery loop exited instead of starting another sweep");
            } finally {
                discovery.close();
                thread.join(5_000);
            }
            assertFalse(thread.isAlive(), "the discovery loop did not stop when closed");
        }
    }

    /**
     * The in-flight cap has to hold, because it is what keeps the sweep inside the process's file
     * descriptor budget. These two ports stand in for the real sweep: connects to the blackhole
     * port hang, so every pending probe holds a descriptor (like a firewalled host), while connects
     * to the closed port are refused, and with a connect timeout of zero they are settled after
     * their deadline — the late resolution that used to make the loop count a probe down twice,
     * once in the selected-key pass and again in the expiry sweep that follows it in the same
     * iteration (a cancelled key only leaves the selector's key set on the next select). The
     * doubled decrement drifted the effective ceiling upwards without bound, so the loop kept
     * opening sockets the cap was supposed to forbid: this test used to see ~220x the configured
     * concurrency before the process ran out of descriptors.
     */
    @Test
    void holdsNoMoreSocketsThanTheConfiguredConcurrency() throws Exception {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Assumptions.assumeTrue(os instanceof com.sun.management.UnixOperatingSystemMXBean,
                "no file descriptor count on this operating system");

        try (ServerSocket blackhole = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket queued = new Socket(InetAddress.getLoopbackAddress(), blackhole.getLocalPort());
             Socket alsoQueued = new Socket(InetAddress.getLoopbackAddress(), blackhole.getLocalPort())) {
            int closedPort;
            try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                closedPort = closed.getLocalPort();
            }
            Assumptions.assumeTrue(connectIsDropped(blackhole.getLocalPort()),
                    "the kernel refused the connect instead of dropping it, so nothing would linger");

            int concurrency = 64;
            Ipv4Space space = new Ipv4Space(List.of(), List.of("127.0.0.1/32"), 1);
            ServerTrackerDiscovery discovery = new ServerTrackerDiscovery(
                    space, (ip, port) -> { }, null,
                    blackhole.getLocalPort() + "," + closedPort, concurrency, 0);
            long baseline = openDescriptors();
            Thread thread = Thread.ofPlatform().daemon(true).name("discovery-test").start(discovery::run);
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                long peak = 0;
                while (System.nanoTime() < deadline) {
                    Thread.sleep(25);
                    peak = Math.max(peak, openDescriptors() - baseline);
                }
                // Room for the JVM's own descriptors and for sockets opened and closed between two
                // samples; what matters is that the count stays near the cap instead of running away.
                long allowed = concurrency * 4L + 256L;
                assertTrue(peak <= allowed,
                        "the discovery loop held " + peak + " sockets with a concurrency of " + concurrency);
            } finally {
                discovery.close();
                thread.join(5_000);
            }
        }
    }

    /**
     * @return whether a connect to {@code port} is dropped rather than refused, i.e. whether the
     *         port is a blackhole for probes
     */
    private static boolean connectIsDropped(int port) throws Exception {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), DROPPED_CONNECT_TIMEOUT_MS);
            return false;
        } catch (SocketTimeoutException e) {
            return true;
        }
    }

    private static long openDescriptors() {
        return ((com.sun.management.UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getOpenFileDescriptorCount();
    }
}
