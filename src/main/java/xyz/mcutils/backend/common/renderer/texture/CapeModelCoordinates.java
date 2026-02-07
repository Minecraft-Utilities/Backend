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
     * Optifine skin part definitions with coordinates and dimensions.
     */
    @Getter
    public enum Optifine {
        CAPE_FRONT(new Coordinates(2, 2, 20, 32), new Coordinates(1, 1, 10, 16));

        private final Coordinates coordinates;
        private final Coordinates legacyCoordinates;

        Optifine(Coordinates coordinates, Coordinates legacyCoordinates) {
            this.coordinates = coordinates;
            this.legacyCoordinates = legacyCoordinates;
        }
    }
}