package xyz.mcutils.backend.common;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import xyz.mcutils.backend.model.dns.impl.ARecord;
import xyz.mcutils.backend.model.dns.impl.SRVRecord;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Braydon
 */
@Slf4j
@UtilityClass
public final class DNSUtils {
    /**
     * The resolver to use for DNS lookups.
     */
    private static SimpleResolver RESOLVER;
    static {
        try {
            RESOLVER = new SimpleResolver();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The prefix to use for Minecraft Java SRV queries.
     */
    private static final String SRV_QUERY_PREFIX = "_minecraft._tcp.%s";

    /**
     * Get the resolved address and port of the
     * given hostname by resolving the SRV records.
     *
     * @param hostname the hostname to resolve
     * @return the resolved address and port, null if none
     */
    @SneakyThrows
    public static SRVRecord resolveSRV(@NonNull String hostname) {
        Record[] records = new Lookup(SRV_QUERY_PREFIX.formatted(hostname), Type.SRV).run(); // Resolve SRV records
        if (records == null) { // No records exist
            return null;
        }
        SRVRecord result = null;
        for (Record record : records) {
            result = new SRVRecord((org.xbill.DNS.SRVRecord) record);
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
        Record[] records = new Lookup(hostname, Type.A).run(); // Resolve A records
        if (records == null) { // No records exist
            return null;
        }
        ARecord result = null;
        for (Record record : records) {
            result = new ARecord((org.xbill.DNS.ARecord) record);
        }
        return result;
    }

     /**
     * Performs a reverse DNS (PTR) lookup using dnsjava with a single server and short timeout (no retries).
     * Returns the hostname, or null on timeout, error, or when no PTR record exists.
     */
    public static String reverseDnsLookup(@NonNull String ip) {
        try {
            long start = System.currentTimeMillis();
            Lookup lookup = new Lookup(ReverseMap.fromAddress(InetAddress.getByName(ip)), Type.PTR);
            lookup.setResolver(RESOLVER);
            Record[] answers = lookup.run();
            long elapsed = System.currentTimeMillis() - start;
            if (answers != null && answers.length > 0 && answers[0] instanceof PTRRecord ptr) {
                String target = ptr.getTarget().toString();
                String hostname = target.endsWith(".") ? target.substring(0, target.length() - 1) : target;
                log.debug("Reverse DNS lookup for {} resolved to {} in {}ms", ip, hostname, elapsed);
                return hostname;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}