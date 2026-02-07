package xyz.mcutils.backend.model.domain.server;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.model.domain.asn.AsnLookup;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.geo.GeoLocation;
import xyz.mcutils.backend.model.domain.serverregistry.ServerRegistryEntry;

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
     * The entry in the server registry.
     */
    @Nullable
    private ServerRegistryEntry registryEntry;
}
