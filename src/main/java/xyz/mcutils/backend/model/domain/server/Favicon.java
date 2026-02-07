package xyz.mcutils.backend.model.domain.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import xyz.mcutils.backend.config.AppConfig;

@Getter
@AllArgsConstructor
public class Favicon {
    /**
     * The raw base64 of the favicon.
     */
    private final String base64;

    /**
     * The url to the favicon.
     */
    private String url;

    /**
     * Create a new favicon for a server.
     *
     * @param base64 the base64 of the favicon
     * @param address the address of the server
     * @return the new favicon
     */
    public static Favicon create(String base64, @NonNull String address) {
        if (base64 == null) { // The server doesn't have a favicon
            return null;
        }
        return new Favicon(base64, AppConfig.INSTANCE.getWebPublicUrl() + "/servers/%s/icon.png".formatted(
                address
        ));
    }
}
