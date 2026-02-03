package xyz.mcutils.backend.model.server;

import org.jetbrains.annotations.Nullable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import xyz.mcutils.backend.model.asn.AsnLookup;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.geo.GeoLocation;
import xyz.mcutils.backend.model.response.IpLookup;
import xyz.mcutils.backend.service.MaxMindService;

/**
 * @author Braydon
 */
@Getter @Setter @ToString @SuperBuilder @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MinecraftServer {

    /**
     * The hostname of the server.
     */
    private String hostname;

    /**
     * The reverse DNS of the server's ip address.
     */
    @Nullable
    private String reverseDns;

    /**
     * The IP address of the server.
     */
    private String ip;

    /**
     * The port of the server.
     */
    private int port;

    /**
     * The DNS records for the server.
     */
    private DNSRecord[] records;

    /**
     * The motd for the server.
     */
    private MOTD motd;

    /**
     * The players on the server.
     */
    private Players players;

    /**
     * The location of the server.
     */
    @Nullable
    private GeoLocation location;

    /**
     * The server's ASN information.
     */
    @Nullable
    private AsnLookup asn;

    /**
     * Populates reverseDns, location, and asn from MaxMind for this server's IP.
     * No-op if ip is null or geo data is already set.
     */
    public void lookupIp() {
        if (ip != null && reverseDns == null && location == null && asn == null) {
            IpLookup ipLookup = MaxMindService.INSTANCE.lookupIp(ip);
            this.reverseDns = ipLookup.reverseDns();
            this.location = ipLookup.location();
            this.asn = ipLookup.asn();
        }
    }
}