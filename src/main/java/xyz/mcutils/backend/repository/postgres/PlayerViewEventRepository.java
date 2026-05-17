package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;

import java.time.Instant;
import java.util.UUID;

public interface PlayerViewEventRepository extends JpaRepository<PlayerViewEventRow, Long> {
    boolean existsByPlayerIdAndIpAddressAndViewedAtAfter(UUID playerId, String ipAddress, Instant after);
}
