package xyz.mcutils.backend.common.renderer.impl.skin;

import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;

public class BodyRenderer extends AbstractFlatBodyRenderer {
    public static final BodyRenderer INSTANCE = new BodyRenderer();

    private static final PartLayout LAYOUT = new PartLayout(
            PlayerModelCoordinates.Skin.FACE,
            PlayerModelCoordinates.Skin.BODY_FRONT,
            PlayerModelCoordinates.Skin.LEFT_ARM_FRONT,
            PlayerModelCoordinates.Skin.RIGHT_ARM_FRONT,
            PlayerModelCoordinates.Skin.LEFT_LEG_FRONT, 8,
            PlayerModelCoordinates.Skin.RIGHT_LEG_FRONT, 4
    );

    @Override
    protected PartLayout getLayout() {
        return LAYOUT;
    }
}
