package xyz.mcutils.backend.repository.redis;

import org.springframework.data.repository.CrudRepository;
import xyz.mcutils.backend.model.player.PlayerUpdateQueueItem;

import java.util.UUID;

/**
 * A cache repository for {@link PlayerUpdateQueueItem}'s.
 *
 * @author Fascinated
 */
public interface PlayerUpdateQueueRepository extends CrudRepository<PlayerUpdateQueueItem, UUID> { }