package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinRepository;

@Controller
public class StatisticsController {

    private final PlayerRepository playerRepository;
    private final CapeRepository capeRepository;
    private final SkinRepository skinRepository;

    @Autowired
    public StatisticsController(PlayerRepository playerRepository, CapeRepository capeRepository, SkinRepository skinRepository) {
        this.playerRepository = playerRepository;
        this.capeRepository = capeRepository;
        this.skinRepository = skinRepository;
    }

    /**
     * Gets the statistics for the app.
     *
     * @return the statistics
     */
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(
                playerRepository.count(),
                skinRepository.count(),
                capeRepository.count()
        );
    }
}
