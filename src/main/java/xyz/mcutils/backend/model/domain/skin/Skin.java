package xyz.mcutils.backend.model.domain.skin;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.EnumUtils;
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
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.service.SkinService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class Skin extends Texture implements PartRenderable<Skin, Skin.SkinPart> {
    public static final String CDN_URL = "https://textures.minecraft.net/texture/%s";

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

    public Skin(String textureId, Model model, boolean legacy, Player player) {
        super(
                textureId,
                CDN_URL.formatted(textureId),
                AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(textureId)
        );

        this.model = model;
        this.legacy = legacy;

        if (player != null) {
            this.parts = new HashMap<>();
            for (SkinPart part : SkinPart.values()) {
                this.parts.put(part.name(), "%s/skins/%s/%s.png".formatted(
                        AppConfig.INSTANCE.getWebPublicUrl(),
                        player.getUniqueId(),
                        part.name().toLowerCase()
                ));
            }
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
     * Gets the skin from a {@link SkinTextureToken}.
     *
     * @param token the skin texture token
     * @param player the player
     * @return the skin, or null if token is null
     */
    public static Skin fromToken(SkinTextureToken token, Player player) {
        if (token == null) {
            return null;
        }
        String textureId = token.getTextureId();
        if (textureId == null) {
            return null;
        }
        String modelName = token.getMetadata() != null && token.getMetadata().getModel() != null
                ? token.getMetadata().getModel().toUpperCase()
                : "DEFAULT";
        return new Skin(textureId, EnumUtils.getEnumConstant(Model.class, modelName), Skin.isLegacySkin(textureId, CDN_URL.formatted(textureId)), player);
    }

    /**
     * Creates a skin from only it's texture id.
     * This is only used for the texture route.
     *
     * @param textureId the texture id of the skin
     * @return the skin
     */
    public static Skin fromId(String textureId) {
        return new Skin(
                textureId,
                Model.DEFAULT,
                false,
                null
        );
    }

    @SneakyThrows
    public static boolean isLegacySkin(String textureId, String textureUrl) {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(SkinService.INSTANCE.getSkinTexture(textureId, textureUrl, false)));
        if (image == null) {
            return false;
        }
        return image.getWidth() == 64 && image.getHeight() == 32;
    }

    /**
     * The model of the skin.
     */
    public enum Model {
        DEFAULT,
        SLIM
    }
}
