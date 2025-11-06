package xyz.mcutils.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.config.Config;

import java.util.Map;

@RestController
@RequestMapping(value = "/")
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
    public Object index() {
        return Map.of(
                "app", "Minecraft Utilities API",
                "version", buildProperties == null ? "dev" : buildProperties.getVersion()
        );
    }
}
