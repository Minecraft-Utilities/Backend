package xyz.mcutils.backend.model.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BodyRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.IsometricHeadRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.VanillaSkinPartRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.IsometricFullBodyRendererBack;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.IsometricFullBodyRendererFront;

import java.awt.image.BufferedImage;

public interface ISkinPart {
    Enum<?>[][] TYPES = { Vanilla.values(), Custom.values() };

    /**
     * The name of the part.
     *
     * @return the part name
     */
    String name();

    /**
     * Should this part be hidden from the
     * player skin part urls list?
     *
     * @return whether this part should be hidden
     */
    boolean hidden();

    /**
     * Renders the skin part for the skin.
     *
     * @param skin the skin
     * @param renderOverlays should the overlays be rendered
     * @param size the output size (height; width derived per part)
     * @return the rendered skin part
     */
    BufferedImage render(Skin skin, boolean renderOverlays, int size);

    /**
     * Get a skin part by the given name.
     *
     * @param name the name of the part
     * @return the part, null if none
     */
    static ISkinPart getByName(String name) {
        name = name.toUpperCase();
        for (Enum<?>[] type : TYPES) {
            for (Enum<?> part : type) {
                if (!part.name().equals(name)) {
                    continue;
                }
                return (ISkinPart) part;
            }
        }
        return null;
    }

    /**
     * The vanilla skin parts.
     * <p>
     *     <a href="https://cdn.fascinated.cc/sXwEKAxm.png">Skin Format</a>
     * </p>
     */
    @Getter
    enum Vanilla implements ISkinPart {
        // Head overlays
        HEAD_OVERLAY_TOP(true, new Coordinates(40, 0), 8, 8),
        HEAD_OVERLAY_FACE(true, new Coordinates(40, 8), 8, 8),
        HEAD_OVERLAY_LEFT(true, new Coordinates(32, 8), 8, 8),
        HEAD_OVERLAY_RIGHT(true, new Coordinates(48, 8), 8, 8),
        HEAD_OVERLAY_BACK(true, new Coordinates(56, 8), 8, 8),
        HEAD_OVERLAY_BOTTOM(true, new Coordinates(48, 0), 8, 8),

        // Body overlays
        BODY_OVERLAY_FRONT(true, new Coordinates(20, 36), 8, 12),
        BODY_OVERLAY_TOP(true, new Coordinates(20, 32), 8, 4),
        BODY_OVERLAY_LEFT(true, new Coordinates(36, 36), 4, 12),
        BODY_OVERLAY_RIGHT(true, new Coordinates(28, 36), 4, 12),
        BODY_OVERLAY_BACK(true, new Coordinates(44, 36), 8, 12),

        // Arm overlays
        LEFT_ARM_OVERLAY_FRONT(true, new Coordinates(52, 52), 4, 12),
        LEFT_ARM_OVERLAY_TOP(true, new Coordinates(52, 48), 4, 4),
        RIGHT_ARM_OVERLAY_FRONT(true, new Coordinates(44, 36), 4, 12),
        RIGHT_ARM_OVERLAY_TOP(true, new Coordinates(44, 48), 4, 4),

        // Leg overlays
        LEFT_LEG_OVERLAY_FRONT(true, new Coordinates(4, 52), 4, 12),
        LEFT_LEG_OVERLAY_TOP(true, new Coordinates(4, 48), 4, 4),
        RIGHT_LEG_OVERLAY_FRONT(true, new Coordinates(4, 36), 4, 12),
        RIGHT_LEG_OVERLAY_TOP(true, new Coordinates(4, 32), 4, 4),

        // Head
        HEAD_TOP(true, new Coordinates(8, 0), 8, 8, HEAD_OVERLAY_TOP),
        FACE(false, new Coordinates(8, 8), 8, 8, HEAD_OVERLAY_FACE),
        HEAD_LEFT(true, new Coordinates(0, 8), 8, 8, HEAD_OVERLAY_LEFT),
        HEAD_RIGHT(true, new Coordinates(16, 8), 8, 8, HEAD_OVERLAY_RIGHT),
        HEAD_BOTTOM(true, new Coordinates(16, 0), 8, 8, HEAD_OVERLAY_BOTTOM),
        HEAD_BACK(true, new Coordinates(24, 8), 8, 8, HEAD_OVERLAY_BACK),

        // Body
        BODY_FRONT(true, new Coordinates(20, 20), 8, 12, BODY_OVERLAY_FRONT),
        BODY_TOP(true, new Coordinates(20, 16), 8, 4, BODY_OVERLAY_TOP),
        BODY_LEFT(true, new Coordinates(36, 20), 4, 12, BODY_OVERLAY_LEFT),
        BODY_RIGHT(true, new Coordinates(28, 20), 4, 12, BODY_OVERLAY_RIGHT),
        BODY_BACK(true, new Coordinates(44, 20), 8, 12, BODY_OVERLAY_BACK),

        // Arms
        LEFT_ARM_TOP(true, new Coordinates(36, 48), 4, 4, LEFT_ARM_OVERLAY_TOP),
        RIGHT_ARM_TOP(true, new Coordinates(44, 16), 4, 4, RIGHT_ARM_OVERLAY_TOP),
        LEFT_ARM_FRONT(true, new Coordinates(36, 52), 4, 12, LEFT_ARM_OVERLAY_FRONT),
        RIGHT_ARM_FRONT(true, new Coordinates(44, 20), 4, 12, RIGHT_ARM_OVERLAY_FRONT),

        // Legs
        LEFT_LEG_TOP(true, new Coordinates(20, 48), 4, 4, LEFT_LEG_OVERLAY_TOP),
        RIGHT_LEG_TOP(true, new Coordinates(4, 16), 4, 4, RIGHT_LEG_OVERLAY_TOP),
        LEFT_LEG_FRONT(true, new Coordinates(20, 52), 4, 12, LEFT_LEG_OVERLAY_FRONT),
        RIGHT_LEG_FRONT(true, new Coordinates(4, 20), 4, 12, RIGHT_LEG_OVERLAY_FRONT);

        /**
         * Should this part be hidden from the
         * player skin part urls list?
         */
        private final boolean hidden;

        /**
         * The coordinates of the part.
         */
        private final Coordinates coordinates;

        /**
         * The width and height of the part.
         */
        private final int width, height;

        /**
         * The overlays of the part.
         */
        private final Vanilla[] overlays;

        Vanilla(boolean hidden, Coordinates coordinates, int width, int height, Vanilla... overlays) {
            this.hidden = hidden;
            this.coordinates = coordinates;
            this.width = width;
            this.height = height;
            this.overlays = overlays;
        }

        @Override
        public boolean hidden() {
            return this.isHidden();
        }

        @Override
        public BufferedImage render(Skin skin, boolean renderOverlays, int size) {
            return VanillaSkinPartRenderer.INSTANCE.render(skin, this, renderOverlays, size);
        }

        /**
         * Is this part a front arm (base or overlay)?
         *
         * @return whether this part is a front arm
         */
        public boolean isFrontArm() {
            return this == LEFT_ARM_FRONT || this == RIGHT_ARM_FRONT
                || this == LEFT_ARM_OVERLAY_FRONT || this == RIGHT_ARM_OVERLAY_FRONT;
        }

        @AllArgsConstructor @Getter
        public static class Coordinates {
            /**
             * The X and Y position of the part.
             */
            private final int x, y;
        }

        /**
         * Model box definitions for the 3D player model. Maps each body part to its
         * base and overlay texture regions and box dimensions.
         */
        public enum ModelBox {
            HEAD(FACE, HEAD_OVERLAY_FACE, 8),
            BODY(BODY_FRONT, BODY_OVERLAY_FRONT, 4),
            LEFT_ARM(LEFT_ARM_FRONT, LEFT_ARM_OVERLAY_FRONT, 4),
            RIGHT_ARM(RIGHT_ARM_FRONT, RIGHT_ARM_OVERLAY_FRONT, 4),
            LEFT_LEG(LEFT_LEG_FRONT, LEFT_LEG_OVERLAY_FRONT, 4),
            RIGHT_LEG(RIGHT_LEG_FRONT, RIGHT_LEG_OVERLAY_FRONT, 4);

            private final Vanilla basePart;
            private final Vanilla overlayPart;
            private final int depth;

            ModelBox(Vanilla basePart, Vanilla overlayPart, int depth) {
                this.basePart = basePart;
                this.overlayPart = overlayPart;
                this.depth = depth;
            }

            /**
             * Gets box UV params (x, y, sizeX, sizeY, sizeZ) for the base layer.
             *
             * @param slim whether the skin uses slim arms
             * @return array of {x, y, sizeX, sizeY, sizeZ}
             */
            public int[] getBaseUv(boolean slim) {
                return getUv(basePart, slim, depth);
            }

            /**
             * Gets box UV params (x, y, sizeX, sizeY, sizeZ) for the overlay layer.
             *
             * @param slim whether the skin uses slim arms
             * @return array of {x, y, sizeX, sizeY, sizeZ}
             */
            public int[] getOverlayUv(boolean slim) {
                return getUv(overlayPart, slim, depth);
            }

            private static int[] getUv(Vanilla part, boolean slim, int sizeZ) {
                int w = part.getWidth();
                if (slim && part.isFrontArm()) {
                    w--;
                }
                return new int[]{
                        part.getCoordinates().getX(),
                        part.getCoordinates().getY(),
                        w,
                        part.getHeight(),
                        sizeZ
                };
            }
        }

        /**
         * Legacy 64×32 skin layout coordinates and upgrade mappings for converting
         * to modern 64×64 format. Legacy skins have no overlays — only base layer.
         * Left arm/leg are created by mirroring the right arm/leg.
         * <p>
         * Each copy rect is {@code {dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2}}.
         * </p>
         */
        public static final class LegacyUpgrade {

            /**
             * Create left leg base (LEFT_LEG_* at 20,52): mirror legacy leg (4,16) → modern left leg.
             * Legacy has one leg; we mirror it to fill the left leg base position.
             */
            public static final int[][] LEFT_LEG_COPIES = {
                { 24, 48, 20, 52, 4, 16, 8, 20 },   // top face
                { 28, 48, 24, 52, 8, 16, 12, 20 },
                { 20, 52, 16, 64, 8, 20, 12, 32 },  // front face
                { 24, 52, 20, 64, 4, 20, 8, 32 },
                { 28, 52, 24, 64, 0, 20, 4, 32 },
                { 32, 52, 28, 64, 12, 20, 16, 32 },
            };

            /**
             * Create left arm base: mirror legacy right arm (40,16)-(56,32) → modern left arm.
             * Left arm spans two regions in 64×64; same texture as right arm, different coords.
             */
            public static final int[][] LEFT_ARM_COPIES = {
                // Region (0,32)-(16,48) — left arm section (not in CLEAR_OVERLAYS)
                { 16, 32, 0, 48, 40, 16, 56, 32 },
                // Region (36,48)-(48,64) — left arm at LEFT_ARM_* (36,52)
                { 40, 48, 36, 52, 44, 16, 48, 20 },   // top
                { 44, 48, 40, 52, 48, 16, 52, 20 },
                { 36, 52, 32, 64, 48, 20, 52, 32 },   // front
                { 40, 52, 36, 64, 44, 20, 48, 32 },
                { 44, 52, 40, 64, 40, 20, 44, 32 },
                { 48, 52, 44, 64, 52, 20, 56, 32 },
            };

            /**
             * Overlay regions to clear — legacy skins have no overlays.
             * HEADZ only: (32,0,64,16) — NOT (32,0,64,32) which would wipe RA at y 16–32.
             * Format: {x1, y1, x2, y2} in 64×64 space.
             */
            public static final int[][] CLEAR_OVERLAYS = {
                { 32, 0, 64, 16 },   // HEADZ only (RA is at y 16–32, must not clear)
                { 0, 32, 16, 48 },   // LAZ (left arm overlay)
                { 16, 32, 40, 48 },  // BODYZ
                { 40, 32, 56, 48 },  // RAZ (right arm overlay)
                { 0, 48, 16, 64 },   // LLZ (left leg overlay)
                { 48, 48, 64, 64 },  // Unused corner
            };
        }
    }

    @AllArgsConstructor @Getter
    enum Custom implements ISkinPart {
        HEAD(IsometricHeadRenderer.INSTANCE),
        FULLBODY_FRONT(IsometricFullBodyRendererFront.INSTANCE),
        FULLBODY_BACK(IsometricFullBodyRendererBack.INSTANCE),
        BODY(BodyRenderer.INSTANCE);

        /**
         * The renderer to use for this part
         */
        private final SkinRenderer<Custom> renderer;

        @Override
        public boolean hidden() {
            return false;
        }

        @Override
        public BufferedImage render(Skin skin, boolean renderOverlays, int size) {
            return renderer.render(skin, this, renderOverlays, size);
        }
    }
}
