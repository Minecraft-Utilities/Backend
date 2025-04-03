package xyz.mcutils.backend.model.player;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@AllArgsConstructor
@Getter @EqualsAndHashCode
public class Cape {
    /**
     * The URL of the cape
     */
    private final String url;

    /**
     * The ID of the cape
     */
    private final String id;

    /**
     * Gets the cape from a {@link JsonObject}.
     *
     * @param json the JSON object
     * @return the cape
     */
    public static Cape fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        String url = json.get("url").getAsString();
        String[] capeUrlParts = url.split("/");

        return new Cape(url, capeUrlParts[capeUrlParts.length - 1]);
    }
}
