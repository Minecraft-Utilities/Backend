package xyz.mcutils.backend.repository;

import org.springframework.data.repository.CrudRepository;
import xyz.mcutils.backend.model.cache.CachedPlayerCapePart;

/**
 * A cache repository for player cape parts.
 * <p>
 * This will allow us to easily lookup a
 * player cape part by its id.
 * </p>
 */
public interface PlayerCapePartCacheRepository extends CrudRepository<CachedPlayerCapePart, String> { }
