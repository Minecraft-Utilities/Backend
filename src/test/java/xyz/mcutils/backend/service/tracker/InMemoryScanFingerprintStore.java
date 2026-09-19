package xyz.mcutils.backend.service.tracker;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link ScanFingerprintStore} for tests. No TTLs: windows are cleared per test.
 */
final class InMemoryScanFingerprintStore implements ScanFingerprintStore {

    final Map<String, Set<String>> fingerprints = new HashMap<>();
    final Map<String, Set<String>> ipPorts = new HashMap<>();

    @Override
    public long recordFingerprint(String fingerprint, String net24, Duration ttl) {
        return fingerprints.computeIfAbsent(fingerprint, k -> new HashSet<>()).add(net24)
                ? fingerprints.get(fingerprint).size()
                : fingerprints.get(fingerprint).size();
    }

    @Override
    public long recordIpPortFingerprint(String ip, int port, String fingerprint, Duration ttl) {
        return ipPorts.computeIfAbsent(ip + ":" + fingerprint, k -> new HashSet<>()).add(Integer.toString(port))
                ? ipPorts.get(ip + ":" + fingerprint).size()
                : ipPorts.get(ip + ":" + fingerprint).size();
    }
}