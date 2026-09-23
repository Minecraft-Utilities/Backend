package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerServerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository.PlayerSightingProjection;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;

import java.util.List;
import java.util.UUID;

/**
 * Public read model for the tracked-server API.
 *
 * <p>Requests never touch the discovery/refresh loops and never expose persistence entities.
 * Honeypot rows and UUIDs are hidden; a tracked player whose sightings only exist on
 * honeypot servers also yields 404 behavior.
 */
@Service
public class TrackerQueryService {

    private static final int PER_PAGE = 50;

    private final ServerTrackerRepository serverTrackerRepository;
    private final PlayerHistoryRepository playerHistoryRepository;
    private final TrackerStatsService trackerStatsService;
    private final boolean trackingEnabled;

    public TrackerQueryService(
            ServerTrackerRepository serverTrackerRepository,
            PlayerHistoryRepository playerHistoryRepository,
            TrackerStatsService trackerStatsService,
            @Value("${mc-utils.server-tracker.tracking.enabled:true}") boolean trackingEnabled
    ) {
        this.serverTrackerRepository = serverTrackerRepository;
        this.playerHistoryRepository = playerHistoryRepository;
        this.trackerStatsService = trackerStatsService;
        this.trackingEnabled = trackingEnabled;
    }

    /**
     * Returns a public, honeypot-excluded page of recently updated tracked servers.
     *
     * @param page one-based page, defaulting to 1
     * @return fixed 50-item page or an empty page when no rows are visible
     * @throws BadRequestException when the page is below 1 or out of range
     */
    public Pagination.Page<TrackedServerSummaryResponse> getServers(int page) {
        if (page < 1) {
            throw new BadRequestException("Invalid page '%s'".formatted(page));
        }
        if (!trackingEnabled) {
            return emptyServerPage(page);
        }

        long totalItems = trackerStatsService.getStats().trackedServers();
        if (totalItems <= 0) {
            return emptyServerPage(page);
        }

        int totalPages = (int) ((totalItems + PER_PAGE - 1) / PER_PAGE);
        if (page > totalPages) {
            throw new BadRequestException("Invalid or out-of-range page '%s'".formatted(page));
        }

        Pagination<TrackedServerSummaryResponse> pagination = new Pagination<TrackedServerSummaryResponse>()
                .setItemsPerPage(PER_PAGE)
                .setTotalItems(totalItems);
        return pagination.getPage(page, callback -> serverTrackerRepository
                .findByHoneypotFalseOrderByLastUpdatedDescUuidAsc(PageRequest.of(page - 1, callback.limit()))
                .stream()
                .map(this::toSummary)
                .toList());
    }

    /**
     * Returns one public server detail.
     *
     * @param uuid canonical tracker server UUID
     * @return full public server representation
     * @throws BadRequestException when the UUID is invalid
     * @throws NotFoundException when no public, non-honeypot server exists for the UUID
     */
    public TrackedServerDetailResponse getServerDetail(String uuid) {
        UUID parsedUuid = UUIDUtils.parseUuid(uuid);
        if (!trackingEnabled) {
            throw new NotFoundException("No public server found for UUID: " + uuid);
        }

        TrackedServerRow row = serverTrackerRepository.findByUuidAndHoneypotFalse(parsedUuid)
                .orElseThrow(() -> new NotFoundException("No public server found for UUID: " + uuid));
        return toDetail(row);
    }

    /**
     * Returns a public page of tracked-player sightings for the requested UUID.
     *
     * @param uuid canonical player UUID
     * @param page one-based page
     * @return paginated sightings
     * @throws BadRequestException when the page is invalid
     * @throws NotFoundException when tracking is disabled or no public sightings exist
     */
    public TrackedPlayerResponse getPlayers(String uuid, int page) {
        UUID parsedUuid = UUIDUtils.parseUuid(uuid);
        if (!trackingEnabled) {
            throw new NotFoundException("Tracked player history is disabled for UUID: " + uuid);
        }
        if (page < 1) {
            throw new BadRequestException("Invalid page '%s'".formatted(page));
        }

        long sightings = playerHistoryRepository.countPublicSightingsByPlayerUuid(parsedUuid);
        if (sightings <= 0) {
            throw new NotFoundException("No public player sightings exist for UUID: " + uuid);
        }

        int totalPages = (int) ((sightings + PER_PAGE - 1) / PER_PAGE);
        if (page > totalPages) {
            throw new BadRequestException("Invalid or out-of-range page '%s'".formatted(page));
        }

        Page<PlayerSightingProjection> sightingPage = playerHistoryRepository.findPublicSightingsByPlayerUuid(
                parsedUuid,
                PageRequest.of(page - 1, PER_PAGE)
        );
        List<TrackedPlayerServerResponse> items = sightingPage.getContent().stream()
                .map(this::toPlayerServerResponse)
                .toList();
        return new TrackedPlayerResponse(parsedUuid, new Pagination.Page<>(items, sightings, PER_PAGE, totalPages));
    }

    private Pagination.Page<TrackedServerSummaryResponse> emptyServerPage(int page) {
        if (page != 1) {
            throw new BadRequestException("Invalid or out-of-range page '%s'".formatted(page));
        }
        return new Pagination<TrackedServerSummaryResponse>()
                .setItemsPerPage(PER_PAGE)
                .setTotalItems(0)
                .empty();
    }

    private TrackedServerSummaryResponse toSummary(TrackedServerRow row) {
        return new TrackedServerSummaryResponse(
                row.getUuid(),
                row.getIp(),
                row.getPort(),
                row.getVersion(),
                row.getProtocol(),
                row.getPlatform(),
                row.getOnlineCount(),
                row.getMaxPlayers(),
                row.getCountry(),
                row.getConsecutiveOffline() == 0,
                row.getLastUpdated()
        );
    }

    private TrackedServerDetailResponse toDetail(TrackedServerRow row) {
        return new TrackedServerDetailResponse(
                row.getUuid(),
                row.getIp(),
                row.getPort(),
                row.getVersion(),
                row.getProtocol(),
                row.getPlatform(),
                row.getOnlineCount(),
                row.getMaxPlayers(),
                row.getCountry(),
                row.getConsecutiveOffline() == 0,
                row.getLastUpdated(),
                row.getFirstSeen(),
                row.getLastRefreshed(),
                row.getConsecutiveOffline(),
                row.getMotd(),
                row.getLatencyMs(),
                row.isModded(),
                row.isPreventsChatReports(),
                row.isEnforcesSecureChat(),
                row.isPreviewsChat(),
                row.getAsn()
        );
    }

    private TrackedPlayerServerResponse toPlayerServerResponse(PlayerSightingProjection projection) {
        return new TrackedPlayerServerResponse(
                projection.getUsername(),
                projection.getFirstSeen(),
                projection.getLastSeen(),
                projection.getTimesSeen(),
                new TrackedServerSummaryResponse(
                        projection.getServerUuid(),
                        projection.getIp(),
                        projection.getPort(),
                        projection.getVersion(),
                        projection.getProtocol(),
                        projection.getPlatform(),
                        projection.getOnlineCount(),
                        projection.getMaxPlayers(),
                        projection.getCountry(),
                        projection.getOnline(),
                        projection.getLastUpdated()
                )
        );
    }
}
