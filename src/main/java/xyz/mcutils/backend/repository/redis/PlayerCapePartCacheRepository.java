package xyz.mcutils.backend.repository.redis;

import org.springframework.data.repository.CrudRepository;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayerCapePart;

/**
 * A cache repository for player cape parts.
 * <p>
 * This will allow us to easily lookup a
 * player cape part by its id.
 * </p>
 */
public interface PlayerCapePartCacheRepository extends CrudRepository<CachedPlayerCapePart, String> { }
