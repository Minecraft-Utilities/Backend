package xyz.mcutils.backend.model.server;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import xyz.mcutils.backend.model.asn.AsnLookup;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.geo.GeoLocation;
import xyz.mcutils.backend.model.response.IpLookup;
import xyz.mcutils.backend.service.MaxMindService;

/**
 * @author Braydon
 */
@Getter @Setter @EqualsAndHashCode
public class MinecraftServer {

    /**
     * The hostname of the server.
     */
    private final String hostname;

    /**
     * The IP address of the server.
     */
    private final String ip;

    /**
     * The port of the server.
     */
    private final int port;

    /**
     * The DNS records for the server.
     */
    private final DNSRecord[] records;

    /**
     * The motd for the server.
     */
    private final MOTD motd;

    /**
     * The players on the server.
     */
    private final Players players;

    /**
     * The location of the server.
     */
    private GeoLocation location;

    /**
     * The server's ASN information.
     */
    private AsnLookup asn;

    public MinecraftServer(String hostname, String ip, int port, DNSRecord[] records, MOTD motd, Players players) {
        this.hostname = hostname;
        this.ip = ip;
        this.port = port;
        this.records = records;
        this.motd = motd;
        this.players = players;

        IpLookup ipLookup = MaxMindService.INSTANCE.lookupIp(ip);
        this.location = ipLookup.location();
        this.asn = ipLookup.asn();
    }
}