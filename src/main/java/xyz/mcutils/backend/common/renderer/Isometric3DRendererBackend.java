package xyz.mcutils.backend.common.renderer;

/**
 * Holder for the active 3D isometric renderer implementation.
 * Set at startup (e.g. by Spring); callers use {@link #get()} to obtain the backend.
 */
public final class Isometric3DRendererBackend {

    private static volatile Isometric3DRenderer instance;

    private Isometric3DRendererBackend() {}

    /**
     * Sets the active 3D renderer implementation.
     *
     * @param renderer the implementation (e.g. software or GPU)
     */
    public static void set(Isometric3DRenderer renderer) {
        instance = renderer;
    }

    /**
     * Returns the active 3D renderer implementation.
     *
     * @return the current implementation
     * @throws IllegalStateException if the backend has not been set (e.g. before Spring startup)
     */
    public static Isometric3DRenderer get() {
        Isometric3DRenderer renderer = instance;
        if (renderer == null) {
            throw new IllegalStateException("Isometric3DRenderer backend has not been set. Ensure Spring has initialized and the renderer bean is registered.");
        }
        return renderer;
    }
}
