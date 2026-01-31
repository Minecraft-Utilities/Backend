package xyz.mcutils.backend.model.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import xyz.mcutils.backend.config.Config;

import java.util.UUID;

/**
 * Player count data for a server.
 */
@AllArgsConstructor
@Getter
public class Players {
    /**
     * The online players on this server.
     */
    private final int online;

    /**
     * The maximum allowed players on this server.
     */
    private final int max;

    /**
     * A sample of players on this server, null or empty if no sample.
     */
    private final Players.Sample[] sample;

    /**
     * A sample player.
     */
    @AllArgsConstructor @Getter @ToString
    public static class Sample {
        /**
         * The unique id of this player.
         */
        @NonNull
        private final UUID id;

        /**
         * The name of this player.
         */
        @NonNull private final String name;

        /**
         * The URL to the player.
         */
        @NonNull
        public String getUrl() {
            return Config.INSTANCE.getWebPublicUrl() + "/player/" + this.id;
        }
    }
}