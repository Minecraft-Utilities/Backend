package xyz.mcutils.backend.common.renderer.impl.gpu;

import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.IsometricLighting;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GPU-accelerated isometric renderer using OpenGL 3.3.
 * All vertex transformation, lighting, and depth sorting done on GPU.
 */
@Slf4j
public class GpuIsometric3DRenderer implements Isometric3DRenderer {

    private static final int INIT_SIZE = 64;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gpu-render");
        t.setDaemon(true);
        return t;
    });

    private volatile long window;
    private volatile int shaderProgram;
    private volatile int uMvp, uNormalMatrix, uSunDirection, uMinBrightness;
    private volatile boolean initialized;

    // Reusable VAO/VBO
    private volatile int vao, vbo;
    private volatile int vboCapacity;
    // PBO for async readback
    private volatile int pbo;
    private volatile int pboCapacity;

    public GpuIsometric3DRenderer() {}

    /**
     * Warms up the GPU context at startup so the first request doesn't pay init cost.
     */
    public void warmUp() {
        ViewParams view = new ViewParams(
                new Vector3(0, 28, -45), new Vector3(0, 16.5, 0),
                45, 35, 512.0 / 869.0);
        render(List.of(), view, 64);
    }

    private void initGlfw() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(INIT_SIZE, INIT_SIZE, "McUtils Offscreen", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new IllegalStateException("Failed to create GLFW window");
        }
        glfwMakeContextCurrent(window);
    }

    private void initGl() {
        GL.createCapabilities();
        
        // Create reusable VAO/VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        vboCapacity = 0;
        
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        
        // Vertex layout: pos(3) + uv(2) + normal(3) = 8 floats = 32 bytes
        int stride = 8 * 4;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);      // position
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);  // uv
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * 4);  // normal
        glEnableVertexAttribArray(2);
        
        glBindVertexArray(0);

        pbo = glGenBuffers();
        pboCapacity = 0;
    }

    private void initShaders() {
        String vertSrc = loadResource("/shaders/isometric/gpu.vert");
        String fragSrc = loadResource("/shaders/isometric/gpu.frag");
        
        int vert = compileShader(GL_VERTEX_SHADER, vertSrc);
        int frag = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        
        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vert);
        glAttachShader(shaderProgram, frag);
        glLinkProgram(shaderProgram);
        
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Shader link error: " + glGetProgramInfoLog(shaderProgram));
        }
        glDeleteShader(vert);
        glDeleteShader(frag);
        
        uMvp = glGetUniformLocation(shaderProgram, "u_mvp");
        uNormalMatrix = glGetUniformLocation(shaderProgram, "u_normalMatrix");
        uSunDirection = glGetUniformLocation(shaderProgram, "u_sunDirection");
        uMinBrightness = glGetUniformLocation(shaderProgram, "u_minBrightness");
        
        glUseProgram(shaderProgram);
        glUniform1i(glGetUniformLocation(shaderProgram, "u_texture"), 0);
    }

    private static String loadResource(String path) {
        try (InputStream in = GpuIsometric3DRenderer.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Resource not found: " + path);
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load shader: " + path, e);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compile error: " + log);
        }
        return shader;
    }

    @Override
    public BufferedImage render(List<TexturedFaces> batches, ViewParams view, int size) {
        try {
            Future<BufferedImage> future = renderExecutor.submit(() -> renderOnGlThread(batches, view, size));
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException("GPU render failed", e);
        }
    }

    private BufferedImage renderOnGlThread(List<TexturedFaces> batches, ViewParams view, int size) {
        if (!initialized) {
            initGlfw();
            initGl();
            initShaders();
            initialized = true;
        }

        int width = (int) Math.round(size * view.aspectRatio());

        long t0 = System.nanoTime();

        // Single pass: build geometry and compute bounds
        GeometryAndBounds geo = buildGeometryAndBounds(batches, view);
        double tBoundsGeometry = (System.nanoTime() - t0) / 1e6;
        double minX = geo.minX, maxX = geo.maxX, minY = geo.minY, maxY = geo.maxY;
        double modelW = Math.max(1, maxX - minX);
        double modelH = Math.max(1, maxY - minY);
        double scale = Math.min((width - 4) / modelW, (size - 4) / modelH);
        double offsetX = (width - modelW * scale) / 2 - minX * scale;
        double offsetY = (size + modelH * scale) / 2 + minY * scale;

        // Build MVP matrix with computed scale
        float[] mvp = buildMvp(view, width, size, scale, offsetX, offsetY);
        float[] normalMatrix = buildNormalMatrix(view.yawDeg(), view.pitchDeg());

        // Create FBO
        int fbo = glGenFramebuffers();
        int colorTex = glGenTextures();
        int depthRbo = glGenRenderbuffers();

        glBindTexture(GL_TEXTURE_2D, colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, size);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            cleanup(fbo, colorTex, depthRbo);
            throw new IllegalStateException("FBO incomplete");
        }

        // Setup render state
        glViewport(0, 0, width, size);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Set uniforms
        glUseProgram(shaderProgram);
        glUniformMatrix4fv(uMvp, false, mvp);
        glUniformMatrix3fv(uNormalMatrix, false, normalMatrix);
        glUniform3f(uSunDirection,
                (float) IsometricLighting.SUN_DIRECTION.getX(),
                (float) IsometricLighting.SUN_DIRECTION.getY(),
                (float) IsometricLighting.SUN_DIRECTION.getZ());
        glUniform1f(uMinBrightness, (float) IsometricLighting.MIN_BRIGHTNESS);

        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);

        // Render each texture batch with a single draw call
        double tUpload = 0, tDraw = 0;
        for (int i = 0; i < batches.size(); i++) {
            TexturedFaces batch = batches.get(i);
            if (batch.faces().isEmpty()) continue;

            long t = System.nanoTime();
            int texId = uploadTexture(batch.texture());
            tUpload += (System.nanoTime() - t) / 1e6;
            glBindTexture(GL_TEXTURE_2D, texId);

            FloatBuffer geometry = geo.geometryPerBatch.get(i);
            int vertexCount = batch.faces().size() * 6;  // 2 triangles per quad

            uploadGeometry(geometry);
            t = System.nanoTime();
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            tDraw += (System.nanoTime() - t) / 1e6;

            glDeleteTextures(texId);
        }

        glBindVertexArray(0);

        // Read result
        long tRead = System.nanoTime();
        BufferedImage result = readPixels(width, size);
        double tReadTotal = (System.nanoTime() - tRead) / 1e6;

        if (log.isDebugEnabled()) {
            log.debug("GPU render profile: bounds+geometry={}ms upload={}ms draw={}ms readPixels+copy={}ms",
                    String.format("%.2f", tBoundsGeometry),
                    String.format("%.2f", tUpload), String.format("%.2f", tDraw),
                    String.format("%.2f", tReadTotal));
        }

        cleanup(fbo, colorTex, depthRbo);
        return result;
    }

    private void cleanup(int fbo, int colorTex, int depthRbo) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(fbo);
        glDeleteTextures(colorTex);
        glDeleteRenderbuffers(depthRbo);
    }

    /**
     * Single pass: build geometry for all batches and compute view-space bounds.
     */
    private GeometryAndBounds buildGeometryAndBounds(List<TexturedFaces> batches, ViewParams view) {
        Vector3 eye = view.eye();
        Vector3 target = view.target();
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(right, fwd));
        double yaw = view.yawDeg();
        double pitch = view.pitchDeg();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        List<FloatBuffer> geometryPerBatch = new ArrayList<>(batches.size());

        for (TexturedFaces batch : batches) {
            List<Face> faces = batch.faces();
            if (faces.isEmpty()) {
                geometryPerBatch.add(org.lwjgl.BufferUtils.createFloatBuffer(0));
                continue;
            }
            int texW = batch.texture().getWidth();
            int texH = batch.texture().getHeight();
            FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(faces.size() * 6 * 8);

            for (Face face : faces) {
                // Project vertices for bounds
                for (Vector3 v : List.of(face.v0(), face.v1(), face.v2(), face.v3())) {
                    Vector3 rotated = Vector3Utils.rotAround(v, target, yaw, pitch);
                    double[] p = Vector3Utils.project(rotated, eye, fwd, right, up);
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }

                // Build geometry (raw world-space, GPU will transform)
                float x0 = (float) face.v0().getX(), y0 = (float) face.v0().getY(), z0 = (float) face.v0().getZ();
                float x1 = (float) face.v1().getX(), y1 = (float) face.v1().getY(), z1 = (float) face.v1().getZ();
                float x2 = (float) face.v2().getX(), y2 = (float) face.v2().getY(), z2 = (float) face.v2().getZ();
                float x3 = (float) face.v3().getX(), y3 = (float) face.v3().getY(), z3 = (float) face.v3().getZ();
                float nx = (float) face.normal().getX();
                float ny = (float) face.normal().getY();
                float nz = (float) face.normal().getZ();
                float u0 = (float) (face.u0() / texW);
                float v0 = (float) (1.0 - face.v0_() / texH);
                float u1 = (float) (face.u1() / texW);
                float v1 = (float) (1.0 - face.v1_() / texH);

                buf.put(x0).put(y0).put(z0).put(u0).put(v0).put(nx).put(ny).put(nz);
                buf.put(x1).put(y1).put(z1).put(u1).put(v0).put(nx).put(ny).put(nz);
                buf.put(x3).put(y3).put(z3).put(u1).put(v1).put(nx).put(ny).put(nz);
                buf.put(x0).put(y0).put(z0).put(u0).put(v0).put(nx).put(ny).put(nz);
                buf.put(x3).put(y3).put(z3).put(u1).put(v1).put(nx).put(ny).put(nz);
                buf.put(x2).put(y2).put(z2).put(u0).put(v1).put(nx).put(ny).put(nz);
            }
            buf.flip();
            geometryPerBatch.add(buf);
        }

        return new GeometryAndBounds(geometryPerBatch, minX, maxX, minY, maxY);
    }

    private record GeometryAndBounds(List<FloatBuffer> geometryPerBatch, double minX, double maxX, double minY, double maxY) {}

    /**
     * Build orthographic MVP matrix that matches software renderer's view.
     * Software does: 1) rotate around target, 2) project to view space, 3) scale to screen
     */
    private float[] buildMvp(ViewParams view, int width, int height, double scale, double offsetX, double offsetY) {
        Vector3 eye = view.eye();
        Vector3 target = view.target();
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(right, fwd));
        
        double yaw = view.yawDeg();
        double pitch = view.pitchDeg();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double cp = Math.cos(pitchRad), sp = Math.sin(pitchRad);
        
        // Step 1: Rotation around target - Rx(pitch) * Ry(yaw)
        // Ry(yaw): x' = x*cy - z*sy, y' = y, z' = x*sy + z*cy
        // Rx(pitch): x' = x, y' = y*cp - z*sp, z' = y*sp + z*cp
        double r00 = cy,       r01 = 0,   r02 = -sy;
        double r10 = -sp * sy, r11 = cp,  r12 = -sp * cy;
        double r20 = cp * sy,  r21 = sp,  r22 = cp * cy;
        
        // Translate to rotate around target (not origin)
        double tx1 = -(r00 * target.getX() + r01 * target.getY() + r02 * target.getZ()) + target.getX();
        double ty1 = -(r10 * target.getX() + r11 * target.getY() + r12 * target.getZ()) + target.getY();
        double tz1 = -(r20 * target.getX() + r21 * target.getY() + r22 * target.getZ()) + target.getZ();
        
        // Step 2: View transform (project to camera space)
        // viewX = dot(world - eye, right), viewY = dot(world - eye, up), viewZ = -dot(world - eye, fwd)
        double v00 = right.getX(), v01 = right.getY(), v02 = right.getZ();
        double v10 = up.getX(),    v11 = up.getY(),    v12 = up.getZ();
        double v20 = -fwd.getX(),  v21 = -fwd.getY(),  v22 = -fwd.getZ();
        
        double tx2 = -(v00 * eye.getX() + v01 * eye.getY() + v02 * eye.getZ());
        double ty2 = -(v10 * eye.getX() + v11 * eye.getY() + v12 * eye.getZ());
        double tz2 = -(v20 * eye.getX() + v21 * eye.getY() + v22 * eye.getZ());
        
        // Combine rotation and view: V * (R * v + t1) = V*R*v + V*t1
        double m00 = v00*r00 + v01*r10 + v02*r20;
        double m01 = v00*r01 + v01*r11 + v02*r21;
        double m02 = v00*r02 + v01*r12 + v02*r22;
        double m03 = v00*tx1 + v01*ty1 + v02*tz1 + tx2;
        
        double m10 = v10*r00 + v11*r10 + v12*r20;
        double m11 = v10*r01 + v11*r11 + v12*r21;
        double m12 = v10*r02 + v11*r12 + v12*r22;
        double m13 = v10*tx1 + v11*ty1 + v12*tz1 + ty2;
        
        double m20 = v20*r00 + v21*r10 + v22*r20;
        double m21 = v20*r01 + v21*r11 + v22*r21;
        double m22 = v20*r02 + v21*r12 + v22*r22;
        double m23 = v20*tx1 + v21*ty1 + v22*tz1 + tz2;
        
        // Step 3: Orthographic projection to NDC using computed scale and offset
        // Software renderer: screenX = viewX * scale + offsetX, screenY = offsetY - viewY * scale
        // Convert to NDC: ndcX = 2*screenX/width - 1, ndcY = 1 - 2*screenY/height
        // Combined: ndcX = (2*scale/width)*viewX + (2*offsetX/width - 1)
        //           ndcY = (2*scale/height)*viewY + (1 - 2*offsetY/height)
        double scaleX = 2.0 * scale / width;
        double scaleY = 2.0 * scale / height;
        double scaleZ = 0.01;  // Depth scale for Z-buffer
        double ndcOffsetX = 2.0 * offsetX / width - 1.0;
        double ndcOffsetY = 1.0 - 2.0 * offsetY / height;
        
        // Combined MVP (column-major for OpenGL)
        // Apply scale to rotation/view matrix, add offset to translation
        return new float[] {
            (float)(scaleX * m00), (float)(scaleY * m10), (float)(scaleZ * m20), 0,
            (float)(scaleX * m01), (float)(scaleY * m11), (float)(scaleZ * m21), 0,
            (float)(scaleX * m02), (float)(scaleY * m12), (float)(scaleZ * m22), 0,
            (float)(scaleX * m03 + ndcOffsetX), (float)(scaleY * m13 + ndcOffsetY), (float)(scaleZ * m23), 1
        };
    }

    /**
     * Build 3x3 normal matrix for transforming normals (rotation only).
     */
    private float[] buildNormalMatrix(double yaw, double pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double cp = Math.cos(pitchRad), sp = Math.sin(pitchRad);
        
        // Same rotation as MVP (Rx * Ry), column-major for OpenGL
        return new float[] {
            (float)cy,        (float)(-sp * sy), (float)(cp * sy),
            0,                (float)cp,         (float)sp,
            (float)(-sy),     (float)(-sp * cy), (float)(cp * cy)
        };
    }

    private void uploadGeometry(FloatBuffer geometry) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int needed = geometry.remaining() * 4;
        if (needed > vboCapacity) {
            glBufferData(GL_ARRAY_BUFFER, geometry, GL_DYNAMIC_DRAW);
            vboCapacity = needed;
        } else {
            glBufferSubData(GL_ARRAY_BUFFER, 0, geometry);
        }
    }

    private int uploadTexture(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);

        // Read directly from DataBufferInt to avoid getRGB format conversion
        if (img.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            int[] pixels = db.getData();
            int off = db.getOffset();
            for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                    int p = pixels[off + y * w + x];
                    buf.put((byte) ((p >> 16) & 0xFF));
                    buf.put((byte) ((p >> 8) & 0xFF));
                    buf.put((byte) (p & 0xFF));
                    buf.put((byte) ((p >> 24) & 0xFF));
                }
            }
        } else {
            // Fallback for non-INT image types
            int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
            for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                    int p = pixels[y * w + x];
                    buf.put((byte) ((p >> 16) & 0xFF));
                    buf.put((byte) ((p >> 8) & 0xFF));
                    buf.put((byte) (p & 0xFF));
                    buf.put((byte) ((p >> 24) & 0xFF));
                }
            }
        }
        buf.flip();

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return tex;
    }

    private BufferedImage readPixels(int width, int height) {
        int size = width * height * 4;
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        if (size > pboCapacity) {
            glBufferData(GL_PIXEL_PACK_BUFFER, size, GL_STREAM_READ);
            pboCapacity = size;
        }
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0);

        ByteBuffer pixels = (ByteBuffer) glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int off = ((DataBufferInt) img.getRaster().getDataBuffer()).getOffset();
        // glReadPixels returns bottom-to-top; BufferedImage row 0 is top. Y-flip.
        for (int y = height - 1; y >= 0; y--) {
            int row = (height - 1 - y) * width;
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                int a = pixels.get(i + 3) & 0xFF;
                data[off + row + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        return img;
    }

    public void dispose() {
        if (!initialized) {
            renderExecutor.shutdown();
            return;
        }
        try {
            renderExecutor.submit(() -> {
                glDeleteProgram(shaderProgram);
                glDeleteVertexArrays(vao);
                glDeleteBuffers(vbo);
                glDeleteBuffers(pbo);
                Callbacks.glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
                glfwTerminate();
                initialized = false;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally {
            renderExecutor.shutdown();
        }
    }
}
