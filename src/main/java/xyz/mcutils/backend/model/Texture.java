package xyz.mcutils.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
public abstract class Texture {
    /**
     * The ID of the texture
     */
    private String textureId;

    /**
     * The raw texture url. E.g. the mojang texture url.
     */
    @JsonIgnore
    private String rawTextureUrl;

    /**
     * Gets the public facing texture url.
     */
    public String textureUrl;
}
