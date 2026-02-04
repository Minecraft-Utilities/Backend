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
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.dns.impl.ARecord;
import xyz.mcutils.backend.model.dns.impl.SRVRecord;

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
    private static Cache<DnsCacheKey, DNSRecord> objectCache;

    public DNSService(
            @Value("${mc-utils.cache.dns.enabled}") boolean cacheEnabled,
            @Value("${mc-utils.cache.dns.ttl}") int objectCacheTtl
    ) {
        if (cacheEnabled) {
            objectCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(objectCacheTtl, TimeUnit.MINUTES)
                    .build();
        }
    }

    /**
     * Get the resolved address and port of the
     * given hostname by resolving the SRV records.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address and port, null if none
     */
    @SneakyThrows
    public static SRVRecord resolveSRV(@NonNull String hostname) {
        DNSRecord dnsRecord = objectCache != null ? objectCache.getIfPresent(new DnsCacheKey(hostname.toUpperCase(), Type.SRV)) : null;
        if (dnsRecord != null) {
            return (SRVRecord) dnsRecord;
        }

        Record[] records = new Lookup(SRV_QUERY_PREFIX.formatted(hostname), Type.SRV).run(); // Resolve SRV records
        if (records == null) { // No records exist
            return null;
        }
        SRVRecord result = null;
        for (Record record : records) {
            result = new SRVRecord((org.xbill.DNS.SRVRecord) record);
        }
        if (objectCache != null && result != null) {
            objectCache.put(new DnsCacheKey(hostname.toUpperCase(), Type.SRV), result);
        }
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
    public static ARecord resolveA(@NonNull String hostname) {
        DNSRecord dnsRecord = objectCache != null ? objectCache.getIfPresent(new DnsCacheKey(hostname.toUpperCase(), Type.A)) : null;
        if (dnsRecord != null) {
            return (ARecord) dnsRecord;
        }

        Record[] records = new Lookup(hostname, Type.A).run(); // Resolve A records
        if (records == null) { // No records exist
            return null;
        }
        ARecord result = null;
        for (Record record : records) {
            result = new ARecord((org.xbill.DNS.ARecord) record);
        }
        if (objectCache != null && result != null) {
            objectCache.put(new DnsCacheKey(hostname.toUpperCase(), Type.A), result);
        }
        return result;
    }

    /**
     * Cache key used for caching DNS queries.
     *
     * @param hostname the hostname that was used in the query
     * @param type the type of the record
     */
    public record DnsCacheKey(String hostname, int type) {}
}