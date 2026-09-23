package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;
import xyz.mcutils.backend.metric.impl.dns.DnsQueryMetric;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.dns.impl.ARecord;
import xyz.mcutils.backend.model.domain.dns.impl.SRVRecord;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Braydon
 */
@Slf4j
@Service
public class DNSService {
    /**
     * The prefix to use for Minecraft Java SRV queries.
     */
    private static final String SRV_QUERY_PREFIX = "_minecraft._tcp.%s";
    /**
     * Negative results (no SRV/A record) are cached too: an absent SRV record is the common
     * case, and each server-cache miss would otherwise fire a fresh blocking DNS query.
     */
    private final Cache<DnsCacheKey, Optional<DNSRecord>> objectCache;
    /**
     * Resolver with a bounded timeout (dnsjava defaults to ~10s with retries); a slow or
     * firewalled DNS server must not stall request threads for seconds at a time.
     */
    private final org.xbill.DNS.SimpleResolver resolver;

    public DNSService(@Value("${mc-utils.cache.dns.enabled}") boolean cacheEnabled, @Value("${mc-utils.cache.dns.ttl}") int objectCacheTtl) {
        this.objectCache = cacheEnabled
                ? CacheBuilder.newBuilder().expireAfterWrite(objectCacheTtl, TimeUnit.MINUTES).maximumSize(10_000).build()
                : null;
        org.xbill.DNS.SimpleResolver resolver = null;
        try {
            resolver = new org.xbill.DNS.SimpleResolver();
            resolver.setTimeout(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Failed to configure DNS resolver timeout, using dnsjava defaults", e);
        }
        this.resolver = resolver;
    }

    /**
     * Get the resolved address and port of the
     * given hostname by resolving the SRV records.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address and port, null if none
     */
    @SneakyThrows
    public SRVRecord resolveSRV(@NonNull String hostname) {
        DnsCacheKey key = new DnsCacheKey(hostname.toUpperCase(), Type.SRV);
        Optional<DNSRecord> cached = objectCache != null ? objectCache.getIfPresent(key) : null;
        if (cached != null) {
            MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.SRV, DnsQueryMetric.Result.CACHE_HIT, 0);
            return (SRVRecord) cached.orElse(null);
        }

        long start = System.currentTimeMillis();
        Lookup lookup = new Lookup(SRV_QUERY_PREFIX.formatted(hostname), Type.SRV);
        if (resolver != null) {
            lookup.setResolver(resolver);
        }
        Record[] records = lookup.run(); // Resolve SRV records
        if (records == null) { // No records exist
            if (objectCache != null) {
                objectCache.put(key, Optional.empty());
            }
            MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.SRV, DnsQueryMetric.Result.NOT_FOUND, System.currentTimeMillis() - start);
            return null;
        }
        SRVRecord result = null;
        for (Record record : records) {
            result = new SRVRecord((org.xbill.DNS.SRVRecord) record);
        }
        if (objectCache != null) {
            objectCache.put(key, Optional.ofNullable(result));
        }
        MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.SRV, DnsQueryMetric.Result.RESOLVED, System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Get the resolved address of the given
     * hostname by resolving the A records.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address, null if none
     */
    @SneakyThrows
    public ARecord resolveA(@NonNull String hostname) {
        DnsCacheKey key = new DnsCacheKey(hostname.toUpperCase(), Type.A);
        Optional<DNSRecord> cached = objectCache != null ? objectCache.getIfPresent(key) : null;
        if (cached != null) {
            MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.A, DnsQueryMetric.Result.CACHE_HIT, 0);
            return (ARecord) cached.orElse(null);
        }

        long start = System.currentTimeMillis();
        Lookup lookup = new Lookup(hostname, Type.A);
        if (resolver != null) {
            lookup.setResolver(resolver);
        }
        Record[] records = lookup.run(); // Resolve A records
        if (records == null) { // No records exist
            if (objectCache != null) {
                objectCache.put(key, Optional.empty());
            }
            MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.A, DnsQueryMetric.Result.NOT_FOUND, System.currentTimeMillis() - start);
            return null;
        }
        ARecord result = null;
        for (Record record : records) {
            result = new ARecord((org.xbill.DNS.ARecord) record);
        }
        if (objectCache != null) {
            objectCache.put(key, Optional.ofNullable(result));
        }
        MetricService.getMetric(DnsQueryMetric.class).record(DnsQueryMetric.QueryType.A, DnsQueryMetric.Result.RESOLVED, System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Cache key used for caching DNS queries.
     *
     * @param hostname the hostname that was used in the query
     * @param type     the type of the record
     */
    public record DnsCacheKey(String hostname, int type) {}
}