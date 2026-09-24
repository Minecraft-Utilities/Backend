package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.dto.request.TrackerServerFilterRequest;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface ServerTrackerRepository extends JpaRepository<TrackedServerRow, UUID>, JpaSpecificationExecutor<TrackedServerRow> {

    Optional<TrackedServerRow> findByIpAndPort(String ip, int port);

    Optional<TrackedServerRow> findByUuidAndHoneypotFalse(UUID uuid);

    @Query("SELECT COUNT(t) FROM TrackedServerRow t WHERE t.honeypot = false")
    long countTrackedServers();

    /**
     * Distinct players currently present in the most recent player sample of an alive,
     * non-honeypot server. The server-advertised {@code online_count} is untrusted (servers can
     * falsify it), so only players actually observed in a sample count. {@code last_seen} is
     * the absorption time of the snapshot that contained the player; the grace seconds absorb
     * the store flush window so a player seen in the latest absorbed sample still matches a
     * marginally newer {@code last_updated}. Players drop out at the first refresh whose sample
     * no longer contains them (refresh cadence is hours, far beyond the grace).
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT ph.player_uuid
                FROM tracker_player_history ph
                JOIN tracker_servers s ON s.uuid = ph.server_uuid
                WHERE s.consecutive_offline = 0
                  AND s.honeypot = false
                  AND ph.last_seen >= s.last_updated - make_interval(secs => ?1)
            ) verified
            """, nativeQuery = true)
    long countVerifiedOnlinePlayers(@Param("graceSeconds") int graceSeconds);

    @Query("SELECT t.country AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.honeypot = false AND t.country IS NOT NULL GROUP BY t.country ORDER BY COUNT(t) DESC")
    List<Breakdown> topCountries(Pageable pageable);

    @Query("SELECT LOWER(COALESCE(t.platform, 'unknown')) AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.honeypot = false GROUP BY LOWER(COALESCE(t.platform, 'unknown')) ORDER BY COUNT(t) DESC")
    List<Breakdown> topPlatforms(Pageable pageable);

    @Query("SELECT t.protocol AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.honeypot = false AND t.protocol IS NOT NULL AND t.protocol <> -1 GROUP BY t.protocol ORDER BY COUNT(t) DESC")
    List<Breakdown> topProtocols(Pageable pageable);

    /**
     * Resolves only the allow-listed public sort fields. A stable deterministic tie-breaker is
     * always appended: {@code uuid} normally, {@code port} for IP ordering (matching the unique
     * {@code (ip, port)} index), and {@code uuid} in the same direction as the primary key for
     * non-default orders.
     */
    static Sort resolveSort(TrackerServerFilterRequest request) {
        String sortField = request.sort() == null ? TrackerServerFilterRequest.DEFAULT_SORT : request.sort();
        Sort.Direction direction = Sort.Direction.fromOptionalString(request.direction() == null ? TrackerServerFilterRequest.DEFAULT_DIRECTION : request.direction())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported tracker sort direction: " + request.direction()));

        String property = switch (sortField) {
            case "lastUpdated" -> "lastUpdated";
            case "onlineCount" -> "onlineCount";
            case "latencyMs" -> "latencyMs";
            case "protocol" -> "protocol";
            case "ip" -> "ip";
            case "country" -> "country";
            default -> throw new IllegalArgumentException("Unsupported tracker sort field: " + sortField);
        };

        Sort.Order tieBreaker;
        if ("ip".equals(sortField)) {
            tieBreaker = new Sort.Order(direction, "port");
        } else if ("lastUpdated".equals(sortField) && direction == Sort.Direction.DESC) {
            tieBreaker = new Sort.Order(Sort.Direction.ASC, "uuid");
        } else {
            tieBreaker = new Sort.Order(direction, "uuid");
        }

        return Sort.by(new Sort.Order(direction, property), tieBreaker);
    }

    /**
     * Builds the mandatory public-read specification. Honeypot exclusion is unconditionally
     * applied and cannot be overridden through the request.
     */
    static Specification<TrackedServerRow> toSpecification(TrackerServerFilterRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("honeypot")));

            String ip = request.ip();
            if (ip != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("ip")),
                        escapeLike(ip.toLowerCase(Locale.ROOT)) + "%",
                        '\\'
                ));
            }

            String country = request.country();
            if (country != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("country")),
                        "%" + escapeLike(country.toLowerCase(Locale.ROOT)) + "%",
                        '\\'
                ));
            }

            String platform = request.platform();
            if (platform != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("platform")),
                        "%" + escapeLike(platform.toLowerCase(Locale.ROOT)) + "%",
                        '\\'
                ));
            }

            Integer protocol = request.protocol();
            if (protocol != null) {
                predicates.add(criteriaBuilder.equal(root.get("protocol"), protocol));
            }

            Integer minOnlinePlayers = request.minOnlinePlayers();
            if (minOnlinePlayers != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("onlineCount"), minOnlinePlayers));
            }

            Integer maxOnlinePlayers = request.maxOnlinePlayers();
            if (maxOnlinePlayers != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("onlineCount"), maxOnlinePlayers));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    interface Breakdown {
        String getGroupKey();

        long getTotal();
    }
}