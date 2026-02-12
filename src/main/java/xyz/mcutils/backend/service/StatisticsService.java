package xyz.mcutils.backend.service;

import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;

@Service
public class StatisticsService {

    private final PlayerService playerService;
    private final SkinService skinService;
    private final CapeService capeService;

    public StatisticsService(PlayerService playerService, SkinService skinService, CapeService capeService) {
        this.playerService = playerService;
        this.skinService = skinService;
        this.capeService = capeService;
    }

    /**
     * Gets the statistics for the app.
     * Uses estimated counts from each service for better performance.
     *
     * @return the statistics
     */
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(
                playerService.getTrackedPlayerCount(),
                skinService.getTrackedSkinCount(),
                capeService.getTrackedCapeCount()
        );
    }
}
