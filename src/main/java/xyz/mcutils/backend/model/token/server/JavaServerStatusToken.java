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
    private final JavaVersion version;
    private final Players players;
    @SerializedName("modinfo")
    private ForgeModInfo modInfo;
    private ForgeData forgeData;
    private final Object description;
    private final String favicon;
    private boolean preventsChatReports;
    private boolean enforcesSecureChat;
    private boolean previewsChat;
    private boolean isModded;

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
