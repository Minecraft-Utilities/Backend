package xyz.mcutils.backend.model.domain.skin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.PartRenderable;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BodyRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.FaceRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.HeadRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBack;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererFront;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.domain.Texture;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class Skin extends Texture implements PartRenderable<Skin, Skin.SkinPart> {
    public static final String CDN_URL = "https://textures.minecraft.net/texture/%s";

    /**
     * The UUID of this skin.
     */
    @JsonIgnore
    private UUID uuid;

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

    /**
     * The number of accounts that have used this skin before.
     */
    private long accountsUsed;

    public Skin(UUID uuid, String textureId, Model model, boolean legacy, long accountsUsed) {
        super(
                textureId,
                CDN_URL.formatted(textureId),
                AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(textureId)
        );
        this.uuid = uuid;
        this.model = model;
        this.legacy = legacy;
        this.accountsUsed = accountsUsed;

        this.parts = new HashMap<>();
        for (SkinPart part : SkinPart.values()) {
            this.parts.put(part.name(), "%s/skins/%s/%s.png".formatted(
                    AppConfig.INSTANCE.getWebPublicUrl(),
                    textureId,
                    part.name().toLowerCase()
            ));
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

    public static boolean isLegacySkin(String textureId, String textureUrl) {
        try {
            BufferedImage image = ImageUtils.decodeImage(SkinService.INSTANCE.getSkinTexture(textureId, textureUrl, false));
            return image.getWidth() == 64 && image.getHeight() == 32;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * The model of the skin.
     */
    public enum Model {
        DEFAULT,
        SLIM
    }

    @Getter
    public enum SkinPart {
        FACE(FaceRenderer.INSTANCE),
        HEAD(HeadRenderer.INSTANCE),
        BODY(BodyRenderer.INSTANCE),
        FULLBODY_FRONT(FullBodyRendererFront.INSTANCE),
        FULLBODY_BACK(FullBodyRendererBack.INSTANCE);

        private final SkinRenderer renderer;

        SkinPart(SkinRenderer renderer) {
            this.renderer = renderer;
        }

    }
}
