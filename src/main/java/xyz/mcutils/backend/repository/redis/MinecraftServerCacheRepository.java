package xyz.mcutils.backend.repository.redis;

import org.springframework.data.repository.CrudRepository;
import xyz.mcutils.backend.model.persistence.redis.CachedMinecraftServer;

/**
 * A cache repository for {@link CachedMinecraftServer}'s.
 *
 * @author Braydon
 */
public interface MinecraftServerCacheRepository extends CrudRepository<CachedMinecraftServer, String> { }