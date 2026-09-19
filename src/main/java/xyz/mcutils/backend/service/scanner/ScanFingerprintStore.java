package xyz.mcutils.backend.service.scanner;

import java.time.Duration;

/**
 * Backend for the honeypot fingerprint windows. The production implementation is Redis-backed
 * (TTL-bounded sets); the seam also allows deterministic in-memory verification.
 */
public interface ScanFingerprintStore {

    /**
     * Records that {@code fingerprint} was observed in {@code net24} and returns the number of
     * distinct /24 subnets the fingerprint is now known from within its TTL window.
     */
    long recordFingerprint(String fingerprint, String net24, Duration ttl);

    /**
     * Records that {@code fingerprint} appeared on {@code port} of {@code ip} and returns the
     * number of distinct ports of that IP the fingerprint is now known from within its TTL window.
     */
    long recordIpPortFingerprint(String ip, int port, String fingerprint, Duration ttl);
}