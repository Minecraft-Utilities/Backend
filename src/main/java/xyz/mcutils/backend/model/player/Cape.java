package xyz.mcutils.backend.model.player;

import com.google.gson.JsonObject;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor @NoArgsConstructor
@Document("capes")
@Getter @EqualsAndHashCode @ToString
public class Cape {
    /**
     * The ID of the cape
     */
    @Id private String id;

    /**
     * The name of the cape
     */
    @Setter private String name = null;

    /**
     * The amount of accounts that have this cape
     */
    @Setter private int accounts = 0;

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

        return new Cape(capeUrlParts[capeUrlParts.length - 1], null, 0);
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
