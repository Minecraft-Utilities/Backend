package xyz.mcutils.backend.model.domain.server.bedrock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.server.MOTD;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;
import xyz.mcutils.backend.model.domain.server.Platform;
import xyz.mcutils.backend.model.domain.server.Players;

/**
 * A Bedrock edition {@link MinecraftServer}.
 *
 * @author Braydon
 */
@Getter @Setter @SuperBuilder @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED, force = true)
public final class BedrockMinecraftServer extends MinecraftServer {
    /**
     * The unique ID of this server.
     */
    @NonNull private String id;

    /**
     * The edition of this server.
     */
    @NonNull private BedrockEdition edition;

    /**
     * The version information of this server.
     */
    @NonNull private BedrockVersion version;

    /**
     * The gamemode of this server.
     */
    @NonNull private BedrockGameMode gamemode;

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
        return BedrockMinecraftServer.builder()
                .id(split[6])
                .hostname(hostname)
                .ip(ip)
                .port(port)
                .records(records)
                .edition(edition)
                .version(version)
                .players(players)
                .motd(motd)
                .gamemode(gameMode)
                .build();
    }
}
