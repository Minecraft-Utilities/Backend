package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTrackerDiscoveryTest {

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
}
