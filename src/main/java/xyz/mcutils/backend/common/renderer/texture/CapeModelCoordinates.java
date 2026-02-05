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
        CAPE_FRONT(new Coordinates(1, 1), 10, 16);

        private final Coordinates coordinates;
        private final int width;
        private final int height;

        Vanilla(Coordinates coordinates, int width, int height) {
            this.coordinates = coordinates;
            this.width = width;
            this.height = height;
        }

        public record Coordinates(int x, int y) {}
    }
}