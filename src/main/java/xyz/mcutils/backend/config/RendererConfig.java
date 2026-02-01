package xyz.mcutils.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.impl.gpu.GpuIsometric3DRenderer;
import xyz.mcutils.backend.common.renderer.impl.software.SoftwareIsometric3DRenderer;

@Slf4j
@Configuration
public class RendererConfig {

    @Bean
    public Isometric3DRenderer isometric3DRenderer() {
        // TEMP: use software renderer for profiling comparison
        return new SoftwareIsometric3DRenderer();
        // try {
        //     GpuIsometric3DRenderer gpu = new GpuIsometric3DRenderer();
        //     gpu.warmUp();
        //     return gpu;
        // } catch (Throwable t) {
        //     log.warn("GPU renderer unavailable, falling back to software: {}", t.getMessage());
        //     if (log.isDebugEnabled()) {
        //         log.debug("GPU renderer init failure", t);
        //     }
        //     return new SoftwareIsometric3DRenderer();
        // }
    }
}
