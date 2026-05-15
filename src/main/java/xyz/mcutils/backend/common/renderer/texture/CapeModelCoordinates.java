package xyz.mcutils.backend.common.renderer.texture;

import lombok.Getter;

/**
 * Coordinates and geometry for the Minecraft player model.
 * Vanilla skin part UVs, model box definitions, and legacy skin upgrade mappings.
 */
public final class CapeModelCoordinates {
    /**
     * Vanilla skin part definitions with coordinates and dimensions.
     */
    @Getter
    public enum Vanilla {
        CAPE_FRONT(new Coordinates(1, 1, 10, 16));

        private final Coordinates coordinates;

        Vanilla(Coordinates coordinates) {
            this.coordinates = coordinates;
        }
    }

    /**
     * 3D model box definition for the cape. UV anchor follows the standard Minecraft cape
     * texture layout (64×32): front at (1,1), back at (12,1), sides at (0,1)/(11,1),
     * top strip at (1,0). Depth is 1 pixel.
     * UV values are {@code {x, y, sizeX, sizeY, sizeZ}} passed to {@link xyz.mcutils.backend.common.renderer.model.ModelUtils#uvFrom}.
     */
    public enum ModelBox {
        CAPE(1, 1, 10, 16, 1);

        private final int[] uv;

        ModelBox(int x, int y, int sizeX, int sizeY, int sizeZ) {
            this.uv = new int[]{x, y, sizeX, sizeY, sizeZ};
        }

        public int[] getUv() {
            return uv;
        }
    }
}