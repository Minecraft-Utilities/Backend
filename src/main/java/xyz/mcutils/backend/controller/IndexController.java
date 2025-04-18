package xyz.mcutils.backend.controller;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.mcutils.backend.config.Config;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinRepository;

import java.util.Map;

@Controller
@RequestMapping(value = "/")
public class IndexController {
    private final PlayerRepository playerRepository;
    private final SkinRepository skinRepository;
    private final CapeRepository capeRepository;

    @Autowired
    public IndexController(@NonNull PlayerRepository playerRepository, @NonNull SkinRepository skinRepository, @NonNull CapeRepository capeRepository) {
        this.playerRepository = playerRepository;
        this.skinRepository = skinRepository;
        this.capeRepository = capeRepository;
    }

    @GetMapping(value = "/")
    public String home(Model model) {
        String publicUrl = Config.INSTANCE.getWebPublicUrl();

        model.addAttribute("public_url", publicUrl);
        model.addAttribute("player_example_url", publicUrl + "/player/Notch");
        model.addAttribute("java_server_example_url", publicUrl + "/server/java/aetheria.cc");
        model.addAttribute("bedrock_server_example_url", publicUrl + "/server/bedrock/geo.hivebedrock.network");
        model.addAttribute("swagger_url", publicUrl + "/swagger-ui.html");
        return "index";
    }

    @GetMapping(value = "/statistics")
    public ResponseEntity<?> getStatistics() {
        return ResponseEntity.ok(Map.of(
                "trackedPlayers", playerRepository.count(),
                "trackedSkins", skinRepository.count(),
                "trackedCapes", capeRepository.count()
        ));
    }
}
