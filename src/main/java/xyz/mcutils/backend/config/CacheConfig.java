package xyz.mcutils.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            buildCache("geoLookup", 30, TimeUnit.MINUTES, 5_000),
            buildCache("skinByTextureId", 12, TimeUnit.HOURS, 500_000),
            buildCache("capeByTextureId", 12, TimeUnit.HOURS, 5_000)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttl, TimeUnit unit, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl, unit)
                .maximumSize(maxSize)
                .build());
    }
}
