package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.repository.mongo.CapeRepository;

import java.util.Optional;

@Service
@Log4j2(topic = "Cape Service")
public class CapeService {
    public static CapeService INSTANCE;

    private final CapeRepository capeRepository;

    @Autowired
    public CapeService(CapeRepository capeRepository) {
        this.capeRepository = capeRepository;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Creates a cape from a cape.
     *
     * @param cape the cape object
     * @return the cape
     */
    public Cape createCape(Cape cape) {
        // Check if the cape already exists
        if (this.capeRepository.existsById(cape.getId())) {
            return cape;
        }

        // Create the cape
        this.capeRepository.save(cape);
        return cape;
    }

    /**
     * Gets a cape for the given id.
     *
     * @param id the id of the cape
     * @return the cape
     */
    public Cape getCape(String id) {
        return getCape(id, null);
    }

    /**
     * Gets a cape for the given id.
     *
     * @param id the id of the cape
     * @param cape the json data to create the cape with (optional)
     * @return the cape
     */
    public Cape getCape(String id, Cape cape) {
        Optional<Cape> optionalCape = this.capeRepository.findById(id);
        if (optionalCape.isEmpty() && cape != null) {
            return createCape(cape);
        }

        if (optionalCape.isEmpty()) {
            log.info("Cape {} not found", id);
            return null;
        }
        return optionalCape.get();
    }

    /**
     * Checks if a cape exists.
     *
     * @param id the id of the cape
     * @return whether the cape exists
     */
    public boolean capeExists(String id) {
        return capeRepository.existsById(id);
    }
}
