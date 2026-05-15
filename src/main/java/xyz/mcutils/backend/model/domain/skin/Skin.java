package xyz.mcutils.backend.model.domain.skin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.renderer.PartRenderable;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BackRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BodyRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.FaceRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.FullBodyIsoRendererBack;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.FullBodyIsoRendererFront;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.HeadIsoRenderer;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.domain.Texture;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class Skin extends Texture implements PartRenderable<Skin, Skin.SkinPart> {
    public static final String CDN_URL = "https://textures.minecraft.net/texture/%s";

    /**
     * The id of this skin.
     */
    private long id;

    /**
     * The number of accounts that have used this skin.
     */
    private long uniqueOwners;

    /**
     * The date this skin was first seen.
     */
    @Setter
    private Instant firstSeen;

    /**
     * The first player seen using this skin (only set on detail responses).
     */
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String firstSeenUsing;

    /**
     * The accounts currently using this skin (only set on detail responses).
     */
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> accountsSeenUsing;

    /**
     * The model for the skin
     */
    private Model model;

    /**
     * The legacy status of the skin
     */
    @Setter
    private boolean legacy;

    /**
     * The parts of the skin
     */
    @Setter
    private Map<String, String> parts;

    public Skin(long id, String textureId, Model model, long uniqueOwners, boolean legacy, Instant firstSeen) {
        super(AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(id), textureId, CDN_URL.formatted(textureId));
        this.id = id;
        this.model = model;
        this.uniqueOwners = uniqueOwners;
        this.legacy = legacy;
        this.firstSeen = firstSeen;

        this.parts = new HashMap<>();
        for (SkinPart part : SkinPart.values()) {
            this.parts.put(part.name(), "%s/skins/%s/%s.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), id, part.name().toLowerCase()));
        }
    }

    /**
     * Checks if a skin is a legacy skin.
     *
     * @param textureUrl the texture url of the skin
     * @return true if the skin is a legacy skin, false otherwise
     */
    public static boolean isLegacySkin(String textureUrl, WebRequest webRequest) {
        try {
            BufferedImage image = ImageUtils.decodeImage(webRequest.getAsByteArray(textureUrl));
            return image.getWidth() == 64 && image.getHeight() == 32;
        } catch (IllegalStateException e) {
            log.debug("Failed to determine legacy skin status for texture URL: {}", textureUrl, e);
            return false;
        }
    }

    @Override
    public Set<SkinPart> getSupportedParts() {
        return EnumSet.allOf(SkinPart.class);
    }

    @Override
    public BufferedImage render(SkinPart part, int size, RenderOptions options) {
        return part.getRenderer().render(this, size, options);
    }

    /**
     * The model of the skin.
     */
    public enum Model {
        DEFAULT, SLIM
    }

    @Getter
    public enum SkinPart {
        // 2D
        FACE(FaceRenderer.INSTANCE),
        BODY(BodyRenderer.INSTANCE),
        BACK(BackRenderer.INSTANCE),

        // Isometric
        HEAD_ISO(HeadIsoRenderer.INSTANCE),
        FULLBODY_ISO_FRONT(FullBodyIsoRendererFront.INSTANCE),
        FULLBODY_ISO_BACK(FullBodyIsoRendererBack.INSTANCE);

        private final SkinRenderer renderer;

        SkinPart(SkinRenderer renderer) {
            this.renderer = renderer;
        }

        public boolean supportsCape() {
            return this == FULLBODY_ISO_FRONT || this == FULLBODY_ISO_BACK;
        }
    }

    public static Skin fromRow(SkinRow skinRow) {
        return new Skin(
                skinRow.getId(),
                skinRow.getTextureId(),
                skinRow.getModel(),
                skinRow.getUniqueOwners(),
                skinRow.isLegacy(),
                skinRow.getFirstSeen()
        );
    }
}
