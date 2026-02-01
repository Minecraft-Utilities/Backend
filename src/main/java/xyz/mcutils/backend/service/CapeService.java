package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.model.player.Cape;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CapeService {
    public static CapeService INSTANCE;

    private final StorageService minioService;

    private final Cache<String, byte[]> capeCache = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    @Autowired
    public CapeService(StorageService minioService) {
        this.minioService = minioService;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param cape the skin to get the image for
     * @return the skin image
     */
    public byte[] getCapeImage(Cape cape) {
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
