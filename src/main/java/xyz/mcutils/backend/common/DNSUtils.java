package xyz.mcutils.backend.common;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.dns.impl.ARecord;
import xyz.mcutils.backend.model.dns.impl.SRVRecord;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Braydon
 */
@UtilityClass
public final class DNSUtils {
    private static final String SRV_QUERY_PREFIX = "_minecraft._tcp.%s";
    
    /**
     * Minimum TTL for DNS cache (60 seconds) to prevent excessive lookups
     */
    private static final long MIN_TTL_SECONDS = 60L;
    
    /**
     * Maximum TTL for DNS cache (1 hour) to prevent stale records
     */
    private static final long MAX_TTL_SECONDS = 3600L;
    
    /**
     * Unified cache for DNS records (both A and SRV).
     * Keys are prefixed: "a:hostname" for A records, "srv:hostname" for SRV records.
     */
    private static final ExpiringMap<String, DNSRecord> DNS_CACHE = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.CREATED)
            .variableExpiration()
            .build();

    /**
     * Get the resolved address and port of the
     * given hostname by resolving the SRV records.
     * Results are cached based on the DNS record's TTL.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address and port, null if none
     */
    @SneakyThrows
    public static SRVRecord resolveSRV(@NonNull String hostname) {
        String normalizedHostname = normalizeHostname(hostname);
        String query = SRV_QUERY_PREFIX.formatted(normalizedHostname);
        return resolveRecord("srv:", normalizedHostname, query, Type.SRV, SRVRecord.class, 
                record -> new SRVRecord((org.xbill.DNS.SRVRecord) record));
    }

    /**
     * Get the resolved address of the given
     * hostname by resolving the A records.
     * Results are cached based on the DNS record's TTL.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address, null if none
     */
    @SneakyThrows
    public static ARecord resolveA(@NonNull String hostname) {
        String normalizedHostname = normalizeHostname(hostname);
        return resolveRecord("a:", normalizedHostname, normalizedHostname, Type.A, ARecord.class,
                record -> new ARecord((org.xbill.DNS.ARecord) record));
    }
    
    /**
     * Generic method to resolve and cache DNS records.
     *
     * @param cacheKeyPrefix the prefix for the cache key (e.g., "a:" or "srv:")
     * @param normalizedHostname the normalized hostname
     * @param query the DNS query string
     * @param recordType the DNS record type
     * @param expectedClass the expected record class
     * @param recordFactory function to create the record from DNS record
     * @return the resolved record, null if none
     * @param <T> the type of DNS record to return
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private static <T extends DNSRecord> T resolveRecord(
            @NonNull String cacheKeyPrefix,
            @NonNull String normalizedHostname,
            @NonNull String query,
            int recordType,
            @NonNull Class<T> expectedClass,
            @NonNull Function<Record, T> recordFactory) {
        
        String cacheKey = cacheKeyPrefix + normalizedHostname;
        
        // Check cache first
        DNSRecord cached = DNS_CACHE.get(cacheKey);
        if (expectedClass.isInstance(cached)) {
            return (T) cached;
        }
        
        // Perform DNS lookup
        Record[] records = new Lookup(query, recordType).run();
        if (records == null || records.length == 0) {
            return null;
        }
        
        // Create record from first result
        T result = recordFactory.apply(records[0]);
        
        // Cache the result with TTL from DNS record
        if (result != null) {
            long ttl = Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, result.getTtl()));
            DNS_CACHE.put(cacheKey, result, ttl, TimeUnit.SECONDS);
        }
        
        return result;
    }
    
    /**
     * Normalize a hostname by removing trailing dots and converting to lowercase.
     *
     * @param hostname the hostname to normalize
     * @return the normalized hostname
     */
    @NonNull
    private static String normalizeHostname(@NonNull String hostname) {
        if (hostname.isEmpty()) {
            return hostname;
        }
        String normalized = hostname.toLowerCase().trim();
        // Remove trailing dots
        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == '.') {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}