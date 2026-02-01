package xyz.mcutils.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;

@Configuration
public class RendererConfig {

    @Bean
    public Isometric3DRenderer isometric3DRenderer() {
        return new Isometric3DRenderer();
    }
}
