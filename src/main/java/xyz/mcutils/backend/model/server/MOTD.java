package xyz.mcutils.backend.model.server;

import lombok.NonNull;
import xyz.mcutils.backend.common.color.ColorUtils;
import xyz.mcutils.backend.config.AppConfig;

import java.util.Arrays;

/**
 * @param raw         The raw motd lines
 * @param clean       The clean motd lines
 * @param html        The html motd lines
 * @param preview     The URL to the server preview image.
 */
public record MOTD(String[] raw, String[] clean, String[] html, String preview) {
    /**
     * Create a new MOTD from a raw string.
     *
     * @param raw the raw motd string
     * @return the new motd
     */
    @NonNull
    public static MOTD create(@NonNull String hostname, int port, @NonNull Platform platform, @NonNull String raw) {
        String[] rawLines = raw.split("\n"); // The raw lines
        return new MOTD(
                rawLines,
                Arrays.stream(rawLines).map(ColorUtils::stripColor).toArray(String[]::new),
                Arrays.stream(rawLines).map(ColorUtils::toHTML).toArray(String[]::new),
                AppConfig.INSTANCE.getWebPublicUrl() + "/server/%s/%s%s/preview.png".formatted(
                        platform.name().toLowerCase(),
                        hostname,
                        port == 25565 || port == 19132 ? "" : ":" + port
                )
        );
    }
}