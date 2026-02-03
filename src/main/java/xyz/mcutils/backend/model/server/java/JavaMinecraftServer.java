package xyz.mcutils.backend.model.server.java;

import org.jetbrains.annotations.Nullable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.ServerUtils;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.server.*;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;

/**
 * @author Braydon
 */
@Setter @Getter @SuperBuilder @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED, force = true)
public final class JavaMinecraftServer extends MinecraftServer {

    /**
     * The version of the server.
     */
    @NonNull private JavaVersion version;

    /**
     * The mods running on this server.
     */
    @Nullable
    private ForgeModInfo modInfo;

    /**
     * The mods running on this server.
     * <p>
     * This is only used for servers
     * running 1.13 and above.
     * </p>
     */
    @Nullable
    private ForgeData forgeData;

    /**
     * The favicon of the server.
     */
    @Nullable
    private Favicon favicon;

    /**
     * Whether the server prevents chat reports.
     */
    private boolean preventsChatReports;

    /**
     * Whether the server enforces secure chat.
     */
    private boolean enforcesSecureChat;

    /**
     * Whether the server has previews chat enabled.
     * <p>
     * Chat Preview sends chat messages to the server as they are typed, even before they're sent.
     * <a href="https://www.minecraft.net/es-mx/article/minecraft-snapshot-22w19a">More information</a>
     * </p>
     */
    private boolean previewsChat;

    /**
     * The mojang blocked status for the server.
     */
    private boolean mojangBlocked;

    /**
     * Create a new Java Minecraft server.
     *
     * @param hostname the hostname of the server
     * @param ip the IP address of the server
     * @param port the port of the server
     * @param token the status token
     * @return the Java Minecraft server
     */
    @NonNull
    public static JavaMinecraftServer create(@NonNull String hostname, String ip, int port, DNSRecord[] records, @NonNull JavaServerStatusToken token) {
        String motdString = token.getDescription() instanceof String ? (String) token.getDescription() : null;
        if (motdString == null) { // Not a string motd, convert from Json
            motdString = LegacyComponentSerializer.builder()
                    .character(LegacyComponentSerializer.SECTION_CHAR)
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build()
                    .serialize(GsonComponentSerializer.gson().deserialize(Constants.GSON.toJson(token.getDescription())));
        }

        return JavaMinecraftServer.builder()
                .hostname(hostname)
                .ip(ip)
                .port(port)
                .records(records)
                .motd(MOTD.create(hostname, Platform.JAVA, motdString))
                .players(Players.create(token.getPlayers()))
                .version(token.getVersion().detailedCopy())
                .favicon(Favicon.create(token.getFavicon(), ServerUtils.getAddress(hostname, port)))
                .modInfo(token.getModInfo())
                .forgeData(token.getForgeData())
                .preventsChatReports(token.isPreventsChatReports())
                .enforcesSecureChat(token.isEnforcesSecureChat())
                .previewsChat(token.isPreviewsChat())
                .build();
    }
}