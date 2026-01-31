package xyz.mcutils.backend.model.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import xyz.mcutils.backend.common.ColorUtils;
import xyz.mcutils.backend.config.Config;

import java.util.Arrays;

@AllArgsConstructor @Getter
public class MOTD {

    /**
     * The raw motd lines
     */
    private final String[] raw;

    /**
     * The clean motd lines
     */
    private final String[] clean;

    /**
     * The html motd lines
     */
    private final String[] html;

    /**
     * The URL to the server preview image.
     */
    private final String preview;

    /**
     * Create a new MOTD from a raw string.
     *
     * @param raw the raw motd string
     * @return the new motd
     */
    @NonNull
    public static MOTD create(@NonNull String hostname, @NonNull Platform platform, @NonNull String raw) {
        String[] rawLines = raw.split("\n"); // The raw lines
        return new MOTD(
                rawLines,
                Arrays.stream(rawLines).map(ColorUtils::stripColor).toArray(String[]::new),
                Arrays.stream(rawLines).map(ColorUtils::toHTML).toArray(String[]::new),
                Config.INSTANCE.getWebPublicUrl() + "/server/%s/preview/%s".formatted(
                        platform.name().toLowerCase(),hostname)
        );
    }
}