package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.ScannedServerRow;

import java.util.Optional;

public interface ServerScannerRepository extends JpaRepository<ScannedServerRow, ScannedServerRow.ScannedServerId> {
    Optional<ScannedServerRow> findByIpAndPort(String ip, int port);
}