package xyz.mcutils.backend.model.player;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor @Document("capes")
@Getter @EqualsAndHashCode
public class Cape {
    /**
     * The ID of the cape
     */
    @Id private final String id;

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
