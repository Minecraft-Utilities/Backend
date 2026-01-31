package xyz.mcutils.backend.model.server.java;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ServerUtils;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.server.*;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;

/**
 * @author Braydon
 */
@Setter @Getter @EqualsAndHashCode(callSuper = false)
public final class JavaMinecraftServer extends MinecraftServer {

    /**
     * The version of the server.
     */
    @NonNull private final JavaVersion version;

    /**
     * The favicon of the server.
     */
    private Favicon favicon;

    /**
     * The mods running on this server.
     */
    private ForgeModInfo modInfo;

    /**
     * The mods running on this server.
     * <p>
     *     This is only used for servers
     *     running 1.13 and above.
     * </p>
     */
    private ForgeData forgeData;

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
     *      Chat Preview sends chat messages to the server as they are typed, even before they're sent.
     *      <a href="https://www.minecraft.net/es-mx/article/minecraft-snapshot-22w19a">More information</a>
     * </p>
     */
    private boolean previewsChat;

    /**
     * The mojang blocked status for the server.
     */
    private boolean mojangBlocked;

    public JavaMinecraftServer(String hostname, String ip, int port, MOTD motd, Players players,
                               DNSRecord[] records, @NonNull JavaVersion version, Favicon favicon, ForgeModInfo modInfo,
                               ForgeData forgeData, boolean preventsChatReports, boolean enforcesSecureChat, boolean previewsChat) {
        super(hostname, ip, port, records, motd, players);
        this.version = version;
        this.favicon = favicon;
        this.modInfo = modInfo;
        this.forgeData = forgeData;
        this.preventsChatReports = preventsChatReports;
        this.enforcesSecureChat = enforcesSecureChat;
        this.previewsChat = previewsChat;
    }

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
                    .serialize(GsonComponentSerializer.gson().deserialize(Main.GSON.toJson(token.getDescription())));
        }

        return new JavaMinecraftServer(
                hostname,
                ip,
                port,
                MOTD.create(hostname, Platform.JAVA, motdString),
                Players.create(token.getPlayers()),
                records,
                token.getVersion().detailedCopy(),
                Favicon.create(token.getFavicon(), ServerUtils.getAddress(hostname, port)),
                token.getModInfo(),
                token.getForgeData(),
                token.isPreventsChatReports(),
                token.isEnforcesSecureChat(),
                token.isPreviewsChat()
        );
    }
}