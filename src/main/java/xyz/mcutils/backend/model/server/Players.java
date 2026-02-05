package xyz.mcutils.backend.model.server;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.common.color.ColorUtils;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player count data for a server.
 *
 * @param online The online players on this server.
 * @param max    The maximum allowed players on this server.
 * @param sample A sample of players on this server, null or empty if no sample.
 * @author Braydon
 */
public record Players(int online, int max, @Nullable Sample[] sample) {
    /**
     * Create new player count data from a token.
     *
     * @param token the token to create from
     * @return the player count data
     */
    @NonNull
    public static Players create(@NonNull JavaServerStatusToken.Players token) {
        List<Sample> samples = null;
        if (token.sample() != null) {
            samples = new ArrayList<>(); // The player samples
            for (JavaServerStatusToken.Players.Sample sample : token.sample()) {
                String href = AppConfig.INSTANCE.getWebPublicUrl() + "/player/" + sample.id();
                samples.add(new Sample(sample.id(), Sample.Name.create(sample.name()), href));
            }
        }
        return new Players(token.online(), token.max(), samples != null ? samples.toArray(new Sample[0]) : null);
    }

    /**
     * A sample player.
     *
     * @param id   The unique id of this player.
     * @param name The name of this player.
     * @param url  The url to view player data for this player sample.
     */
    public record Sample(@NonNull UUID id, @NonNull Players.Sample.Name name, @NonNull String url) {
        /**
         * The name of a sample player.
         *
         * @param raw   The raw name.
         * @param clean The clean name (no color codes).
         * @param html  The HTML name.
         */
        public record Name(@NonNull String raw, @NonNull String clean, @NonNull String html) {
            /**
             * Create a new name from a raw string.
             *
             * @param raw the raw name string
             * @return the new name
             */
            @NonNull
            public static Players.Sample.Name create(@NonNull String raw) {
                return new Name(raw, ColorUtils.stripColor(raw), ColorUtils.toHTML(raw));
            }
        }
    }
}