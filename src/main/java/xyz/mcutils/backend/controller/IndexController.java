package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.config.Config;
import xyz.mcutils.backend.model.response.IndexResponse;

@RestController
@RequestMapping(value = "/")
@Tag(name = "Index Controller")
public class IndexController {
    /**
     * The build properties of the
     * app, null if the app is not built.
     */
    private final BuildProperties buildProperties;

    @Autowired
    public IndexController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping(value = "/")
    public IndexResponse index() {
        String publicUrl = Config.INSTANCE.getWebPublicUrl();

        return new IndexResponse(
                "Minecraft Utilities API",
                buildProperties == null ? "dev" : buildProperties.getVersion(),
                publicUrl + "/swagger-ui.html"
        );
    }
}
