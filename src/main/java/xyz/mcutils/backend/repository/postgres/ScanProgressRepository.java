package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.ScanProgressRow;

public interface ScanProgressRepository extends JpaRepository<ScanProgressRow, Integer> {
}