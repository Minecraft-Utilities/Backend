package xyz.mcutils.backend.model.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.NonNull;
import xyz.mcutils.backend.common.ColorUtils;
import xyz.mcutils.backend.config.Config;

import java.util.Arrays;

/**
 * @param raw         The raw motd lines
 * @param clean       The clean motd lines
 * @param html        The html motd lines
 * @param preview     The URL to the server preview image.
 * @param htmlPreview The URL to the server HTML motd preview.
 */
public record MOTD(String[] raw, String[] clean, String[] html, String preview, String htmlPreview) {
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
                        platform.name().toLowerCase(),
                        hostname
                ),
                Config.INSTANCE.getWebPublicUrl() + "/server/%s/html-preview/%s".formatted(
                        platform.name().toLowerCase(),
                        hostname
                )
        );
    }

    /**
     * Generates an HTML representation for the MOTD.
     *
     * @param server the server to generate the HTML for
     * @return the generated HTML
     */
    @JsonIgnore
    public String generateHtmlPreview(MinecraftServer server) {
        StringBuilder builder = new StringBuilder();
        for (String line : this.html()) {
            builder.append(line).append("<br>");
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <style>
                        @font-face {
                            font-family: "Minecraft";
                            src: url("https://cdn.fascinated.cc/minecraft-font.ttf") format("truetype");
                            font-weight: normal;
                            font-style: normal;
                        }
                        body {
                            margin: 0;
                            background-image: url("https://cdn.fascinated.cc/server_background.png");
                            background-repeat: repeat;
                            font-family: "Minecraft", system-ui, sans-serif;
                            font-size: 20px;
                            line-height: 1.4;
                        }
                    </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(
                server.getHostname(),
                builder.toString()
        );
    }
}