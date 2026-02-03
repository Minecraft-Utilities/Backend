package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.model.player.Cape;

import java.util.concurrent.TimeUnit;

@Service @Slf4j
public class CapeService {
    private final StorageService minioService;

    private final Cache<String, byte[]> capeCache;

    @Autowired
    public CapeService(@Value("${mc-utils.cache.ttl.cape-textures}") int cacheTtl, StorageService minioService) {
        this.capeCache = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheTtl, TimeUnit.MINUTES)
                .build();
        this.minioService = minioService;
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param cape the skin to get the image for
     * @return the skin image
     */
    public byte[] getCapeTexture(Cape cape) {
        return this.capeCache.asMap().computeIfAbsent(cape.getId(), _ -> {
            byte[] capeImage = minioService.get(StorageService.Bucket.CAPES, cape.getId() + ".png");
            if (capeImage == null) {
                log.debug("Downloading skin image for skin {}", cape.getId());
                capeImage = PlayerUtils.getImage(cape.getMojangTextureUrl());
                if (capeImage == null) {
                    throw new IllegalStateException("Cape with id '%s' was not found".formatted(cape.getId()));
                }
                minioService.upload(StorageService.Bucket.CAPES, cape.getId() + ".png", MediaType.IMAGE_PNG_VALUE, capeImage);
                log.debug("Saved cape image for skin {}", cape.getId());
            }

            return capeImage;
        });
    }
}
