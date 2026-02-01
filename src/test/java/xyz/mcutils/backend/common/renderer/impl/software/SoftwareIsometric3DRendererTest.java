package xyz.mcutils.backend.common.renderer.impl.software;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoftwareIsometric3DRendererTest {

    @Test
    void render_producesNonBlankOutput() {
        // Minimal texture (64x64, single opaque red pixel in top-left)
        BufferedImage texture = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, 0xFFFF0000);

        // Single quad facing camera: a 8x8 box face at z=0
        List<Face> faces = List.of(new Face(
                new Vector3(8, 8, 0), new Vector3(0, 8, 0),
                new Vector3(8, 0, 0), new Vector3(0, 0, 0),
                0, 0, 8, 8,
                new Vector3(0, 0, -1)
        ));

        Isometric3DRenderer.ViewParams view = new Isometric3DRenderer.ViewParams(
                new Vector3(0, 28, -45), new Vector3(0, 16.5, 0),
                45, 35, 512.0 / 869.0);

        Isometric3DRenderer renderer = new Isometric3DRenderer();
        BufferedImage result = renderer.render(texture, faces, view, 256);

        assertNotNull(result);
        assertEquals(256, result.getHeight());

        // Should have at least some non-transparent pixels
        int opaqueCount = 0;
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                if (((result.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    opaqueCount++;
                }
            }
        }
        assertTrue(opaqueCount > 0, "Rendered image should have visible pixels, got " + opaqueCount);
    }
}
