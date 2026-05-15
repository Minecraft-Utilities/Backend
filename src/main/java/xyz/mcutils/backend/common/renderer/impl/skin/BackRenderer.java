package xyz.mcutils.backend.common.renderer.impl.skin;

import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;

public class BackRenderer extends AbstractFlatBodyRenderer {
    public static final BackRenderer INSTANCE = new BackRenderer();

    private static final PartLayout LAYOUT = new PartLayout(
            PlayerModelCoordinates.Skin.HEAD_BACK,
            PlayerModelCoordinates.Skin.BODY_BACK,
            PlayerModelCoordinates.Skin.LEFT_ARM_BACK,
            PlayerModelCoordinates.Skin.RIGHT_ARM_BACK,
            PlayerModelCoordinates.Skin.LEFT_LEG_BACK, 4,
            PlayerModelCoordinates.Skin.RIGHT_LEG_BACK, 8
    );

    @Override
    protected PartLayout getLayout() {
        return LAYOUT;
    }
}
