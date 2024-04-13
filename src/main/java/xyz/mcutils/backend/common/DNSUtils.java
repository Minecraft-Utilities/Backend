package xyz.mcutils.backend.common;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;
import xyz.mcutils.backend.model.dns.impl.ARecord;
import xyz.mcutils.backend.model.dns.impl.SRVRecord;

/**
 * @author Braydon
 */
@UtilityClass
public final class DNSUtils {
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
}