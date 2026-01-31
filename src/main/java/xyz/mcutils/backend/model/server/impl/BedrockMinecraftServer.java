package xyz.mcutils.backend.model.server.impl;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.server.MOTD;
import xyz.mcutils.backend.model.server.MinecraftServer;
import xyz.mcutils.backend.model.server.Platform;
import xyz.mcutils.backend.model.server.Players;
import xyz.mcutils.backend.model.server.bedrock.BedrockEdition;
import xyz.mcutils.backend.model.server.bedrock.BedrockGameMode;
import xyz.mcutils.backend.model.server.bedrock.BedrockVersion;

/**
 * A Bedrock edition {@link MinecraftServer}.
 *
 * @author Braydon
 */
@Getter @ToString(callSuper = true) @EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public final class BedrockMinecraftServer extends MinecraftServer {
    /**
     * The unique ID of this server.
     */
    @EqualsAndHashCode.Include @NonNull private final String id;

    /**
     * The edition of this server.
     */
    @NonNull private final BedrockEdition edition;

    /**
     * The version information of this server.
     */
    @NonNull private final BedrockVersion version;

    /**
     * The gamemode of this server.
     */
    @NonNull private final BedrockGameMode gamemode;

    private BedrockMinecraftServer(@NonNull String id, @NonNull String hostname, String ip, int port, @NonNull DNSRecord[] records,
                                   @NonNull BedrockEdition edition, @NonNull BedrockVersion version, @NonNull Players players, @NonNull MOTD motd,
                                   @NonNull BedrockGameMode gamemode) {
        super(hostname, ip, port, records, motd, players);
        this.id = id;
        this.edition = edition;
        this.version = version;
        this.gamemode = gamemode;
    }

    /**
     * Create a new Bedrock Minecraft server.
     * <p>
     *     <a href="https://wiki.vg/Raknet_Protocol#Unconnected_Pong">Token Format</a>
     * </p>
     *
     * @param hostname the hostname of the server
     * @param ip the IP address of the server
     * @param port the port of the server
     * @param token the status token
     * @return the Bedrock Minecraft server
     */
    @NonNull
    public static BedrockMinecraftServer create(@NonNull String hostname, String ip, int port, DNSRecord[] records, @NonNull String token) {
        String[] split = token.split(";"); // Split the token
        BedrockEdition edition = BedrockEdition.valueOf(split[0]);
        BedrockVersion version = new BedrockVersion(Integer.parseInt(split[2]), split[3]);
        Players players = new Players(Integer.parseInt(split[4]), Integer.parseInt(split[5]), null);
        MOTD motd = MOTD.create(hostname, Platform.BEDROCK, split[1] + "\n" + split[7]);
        BedrockGameMode gameMode = new BedrockGameMode(split[8], split.length > 9 ? Integer.parseInt(split[9]) : -1);
        return new BedrockMinecraftServer(
                split[6],
                hostname,
                ip,
                port,
                records,
                edition,
                version,
                players,
                motd,
                gameMode
        );
    }
}
