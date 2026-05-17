package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;

public interface PlayerViewEventRepository extends JpaRepository<PlayerViewEventRow, Long> { }
