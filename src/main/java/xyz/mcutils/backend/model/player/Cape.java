package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.*;

@AllArgsConstructor @NoArgsConstructor
@Getter @EqualsAndHashCode @ToString
public class Cape {
    /**
     * The ID of the cape
     */
    @JsonIgnore private String id;

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

        return new Cape(capeUrlParts[capeUrlParts.length - 1]);
    }

    /**
     * Gets the URL for this cape.
     *
     * @return the url for the cape
     */
    public String getUrl() {
        return "https://textures.minecraft.net/texture/" + this.id;
    }
}
