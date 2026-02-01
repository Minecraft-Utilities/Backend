package xyz.mcutils.backend.model.token.server;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import xyz.mcutils.backend.model.server.java.ForgeData;
import xyz.mcutils.backend.model.server.java.ForgeModInfo;
import xyz.mcutils.backend.model.server.java.JavaVersion;

import java.util.UUID;

/**
 * @author Braydon
 */
@AllArgsConstructor @Getter @ToString
public final class JavaServerStatusToken {

    /**
     * The version of the server.
     */
    private final JavaVersion version;

    /**
     * The players on the server.
     */
    private final Players players;

    /**
     * The mods running on this server.
     */
    @SerializedName("modinfo")
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
     * The motd of the server.
     */
    private final Object description;

    /**
     * The favicon of the server.
     */
    private final String favicon;

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
     * Player count data for a server.
     *
     * @param online The online players on this server.
     * @param max    The maximum allowed players on this server.
     * @param sample A sample of players on this server, null or empty if no sample.
     */
    public record Players(int online, int max, Sample[] sample) {
        /**
         * A sample player.
         *
         * @param id   The unique id of this player.
         * @param name The name of this player.
         */
        public record Sample(@NonNull UUID id, @NonNull String name) { }
    }
}
