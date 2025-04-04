package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.repository.mongo.SkinRepository;

import java.util.Optional;

@Service
@Log4j2(topic = "Skin Service")
public class SkinService {
    public static SkinService INSTANCE;

    private final SkinRepository skinRepository;

    @Autowired
    public SkinService(SkinRepository skinRepository) {
        this.skinRepository = skinRepository;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Creates a skin from a skin.
     *
     * @param skin the skin object
     * @return the skin
     */
    public Skin createSkin(Skin skin) {
        // Check if the skin already exists
        if (this.skinRepository.existsById(skin.getId())) {
            return skin;
        }

        // Create the skin
        this.skinRepository.save(skin);
        return skin;
    }

    /**
     * Gets a skin for the given id.
     *
     * @param id the id of the skin
     * @return the skin
     */
    public Skin getSkin(String id) {
        return getSkin(id, null);
    }

    /**
     * Gets a skin for the given id.
     *
     * @param id the id of the skin
     * @param skin the json data to create the skin with (optional)
     * @return the skin
     */
    public Skin getSkin(String id, Skin skin) {
        Optional<Skin> optionalSkin = this.skinRepository.findById(id);
        if (optionalSkin.isEmpty() && skin != null) {
            return createSkin(skin);
        }

        if (optionalSkin.isEmpty()) {
            log.info("Skin {} not found", id);
            return null;
        }
        return optionalSkin.get();
    }

    /**
     * Checks if a skin exists.
     *
     * @param id the id of the skin
     * @return whether the skin exists
     */
    public boolean skinExists(String id) {
        return skinRepository.existsById(id);
    }
}
