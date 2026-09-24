package xyz.mcutils.backend.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Query parameters accepted by the public tracked-server collection endpoint.
 * Text filters are normalized when the record is constructed; enum-like values remain
 * unnormalized so invalid API input is rejected instead of being silently corrected.
 */
public record TrackerServerFilterRequest(
        @Min(value = 1, message = "page must be at least 1") Integer page,
        String ip,
        String country,
        String platform,
        Integer protocol,
        @Min(value = 0, message = "minOnlinePlayers must not be negative") Integer minOnlinePlayers,
        @Min(value = 0, message = "maxOnlinePlayers must not be negative") Integer maxOnlinePlayers,
        @Pattern(
                regexp = "lastUpdated|onlineCount|latencyMs|protocol|ip|country",
                message = "sort must be one of: lastUpdated, onlineCount, latencyMs, protocol, ip, country"
        ) String sort,
        @Pattern(regexp = "asc|desc", message = "direction must be one of: asc, desc") String direction
) {

    public static final String DEFAULT_SORT = "lastUpdated";
    public static final String DEFAULT_DIRECTION = "desc";

    public TrackerServerFilterRequest {
        ip = normalizeText(ip);
        country = normalizeText(country);
        platform = normalizeText(platform);
    }

    public static TrackerServerFilterRequest noFilters() {
        return noFilters(1);
    }

    public static TrackerServerFilterRequest noFilters(int page) {
        return new TrackerServerFilterRequest(page, null, null, null, null, null, null, null, null);
    }

    public int pageNumber() {
        return page == null ? 1 : page;
    }

    public boolean hasValidPlayerBounds() {
        return (minOnlinePlayers == null || minOnlinePlayers >= 0)
                && (maxOnlinePlayers == null || maxOnlinePlayers >= 0)
                && (minOnlinePlayers == null || maxOnlinePlayers == null || minOnlinePlayers <= maxOnlinePlayers);
    }

    public boolean hasValidSort() {
        return sort == null
                || sort.equals("lastUpdated")
                || sort.equals("onlineCount")
                || sort.equals("latencyMs")
                || sort.equals("protocol")
                || sort.equals("ip")
                || sort.equals("country");
    }

    public boolean hasValidDirection() {
        return direction == null || direction.equals("asc") || direction.equals("desc");
    }

    @AssertTrue(message = "minOnlinePlayers must not exceed maxOnlinePlayers")
    public boolean isPlayerRangeValid() {
        return hasValidPlayerBounds();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
