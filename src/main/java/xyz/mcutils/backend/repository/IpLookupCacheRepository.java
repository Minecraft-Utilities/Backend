package xyz.mcutils.backend.repository;

import org.springframework.data.repository.CrudRepository;
import xyz.mcutils.backend.model.cache.CachedIpLookup;

/**
 * A cache repository for {@link CachedIpLookup}'s.
 *
 * @author Braydon
 */
public interface IpLookupCacheRepository extends CrudRepository<CachedIpLookup, String> { }