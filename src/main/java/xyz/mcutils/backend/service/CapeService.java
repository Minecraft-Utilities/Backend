package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.model.player.Cape;

@Service @Slf4j
public class CapeService {
    private final StorageService minioService;

    @Autowired
    public CapeService(StorageService minioService) {
        this.minioService = minioService;
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param cape the skin to get the image for
     * @return the skin image
     */
    public byte[] getCapeTexture(Cape cape) {
        byte[] capeBytes = minioService.get(StorageService.Bucket.CAPES, cape.getId() + ".png");
        if (capeBytes == null) {
            log.debug("Downloading skin image for skin {}", cape.getId());
            capeBytes = PlayerUtils.getImage(cape.getMojangTextureUrl());
            if (capeBytes == null) {
                throw new IllegalStateException("Cape with id '%s' was not found".formatted(cape.getId()));
            }
            minioService.upload(StorageService.Bucket.CAPES, cape.getId() + ".png", MediaType.IMAGE_PNG_VALUE, capeBytes);
            log.debug("Saved cape image for skin {}", cape.getId());
        }
        return capeBytes;
    }
}
