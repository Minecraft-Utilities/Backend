package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.request.TrackerServerFilterRequest;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerSearchResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerServerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository.PlayerSightingProjection;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;

import java.util.List;
import java.util.Objects;
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
    private static final int SEARCH_LIMIT = 10;

    private final ServerTrackerRepository serverTrackerRepository;
    private final PlayerHistoryRepository playerHistoryRepository;
    private final boolean trackingEnabled;

    public TrackerQueryService(
            ServerTrackerRepository serverTrackerRepository,
            PlayerHistoryRepository playerHistoryRepository,
            @Value("${mc-utils.server-tracker.tracking.enabled:true}") boolean trackingEnabled
    ) {
        this.serverTrackerRepository = serverTrackerRepository;
        this.playerHistoryRepository = playerHistoryRepository;
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
        return getServers(TrackerServerFilterRequest.noFilters(page));
    }

    /**
     * Returns a public, honeypot-excluded page of tracked servers matching the request.
     *
     * @param request normalized filter/sort query parameters
     * @return fixed 50-item page with totals computed from the filtered result set
     * @throws BadRequestException when the page, player bounds, sort, or direction is invalid
     */
    public Pagination.Page<TrackedServerSummaryResponse> getServers(TrackerServerFilterRequest request) {
        Objects.requireNonNull(request, "request");
        int page = request.pageNumber();
        if (page < 1) {
            throw new BadRequestException("Invalid page '%s'".formatted(page));
        }
        if (!request.hasValidPlayerBounds()) {
            throw new BadRequestException("Invalid player bounds: minOnlinePlayers=%s, maxOnlinePlayers=%s"
                    .formatted(request.minOnlinePlayers(), request.maxOnlinePlayers()));
        }
        if (!request.hasValidSort()) {
            throw new BadRequestException("Invalid sort '%s'".formatted(request.sort()));
        }
        if (!request.hasValidDirection()) {
            throw new BadRequestException("Invalid direction '%s'".formatted(request.direction()));
        }

        if (!trackingEnabled) {
            return emptyServerPage(page);
        }

        Sort sort = ServerTrackerRepository.resolveSort(request);
        Specification<TrackedServerRow> specification = ServerTrackerRepository.toSpecification(request);
        Page<TrackedServerRow> result = serverTrackerRepository.findAll(
                specification,
                PageRequest.of(page - 1, PER_PAGE, sort)
        );

        long totalItems = result.getTotalElements();
        if (totalItems <= 0) {
            return emptyServerPage(page);
        }

        int totalPages = result.getTotalPages();
        if (page > totalPages) {
            throw new BadRequestException("Invalid or out-of-range page '%s'".formatted(page));
        }

        return new Pagination.Page<>(
                result.getContent().stream().map(this::toSummary).toList(),
                totalItems,
                PER_PAGE,
                totalPages
        );
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
     * Searches players whose usernames have public server-tracker history.
     *
     * @param query username prefix
     * @return matching tracked players, limited to ten results
     */
    public List<TrackedPlayerSearchResponse> searchPlayers(String query) {
        String normalizedQuery = query.trim();
        if (!trackingEnabled || normalizedQuery.isEmpty()) {
            return List.of();
        }

        return playerHistoryRepository.searchPublicPlayers(normalizedQuery, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .map(projection -> new TrackedPlayerSearchResponse(
                        projection.getPlayerUuid(),
                        projection.getUsername(),
                        projection.getSkinId()
                ))
                .toList();
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
