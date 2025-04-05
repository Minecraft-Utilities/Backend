package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.repository.mongo.CapeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Log4j2(topic = "Cape Service")
public class CapeService {
    public static CapeService INSTANCE;
    public static final Map<String, String> CAPE_NAMES = new HashMap<>();
    static {
        CAPE_NAMES.put("b32d8c1671c2936e81ec7e711e2af8e4", "Prismarine");
        CAPE_NAMES.put("e71149cdca1d763faf5d4549edc0620b", "dB");
        CAPE_NAMES.put("47d30b5dd95e65e696fc9ff186d8ee33", "Millionth Customer");
        CAPE_NAMES.put("6206c2b3261ba9b033aae57e32121a86", "Snowman");
        CAPE_NAMES.put("f0b8db3ae2d2857c6d262fcbc2aa4737", "Spade");
        CAPE_NAMES.put("008daa52d7b3e61b686f7cafa3680c0f", "Translator (Japanese)");
        CAPE_NAMES.put("04ddcbd316ec68364238697174fb1c61", "Birthday");
        CAPE_NAMES.put("c7091e237e7562e73990a97dda5bc554", "Test");
        CAPE_NAMES.put("22923c6efb6deee96bd7ecd87086bbb8", "Turtle");
        CAPE_NAMES.put("bfa8cc9d7af57fd856890af7c2bf2a4a", "Valentine");
        CAPE_NAMES.put("a2b113cab4c553b9b306f25e2d599513", "Translator (Chinese)");
        CAPE_NAMES.put("1044b1f5dfc2626b573d8d74a9307a7f", "Scrolls");
        CAPE_NAMES.put("bd64503808bc49c8d80e327754894d4f", "Cobalt");
        CAPE_NAMES.put("7c07865bdc148efb0d1eb95829eaf11a", "Mojang Classic");
        CAPE_NAMES.put("172e4e9fd6c0afdf086b8bdb58c0bad5", "Mojira Moderator");
        CAPE_NAMES.put("a944bd437bb6d12822f740c55ee1a3c9", "Translator");
        CAPE_NAMES.put("8fb7780634ea6c0729fea7a7e6f50c28", "Mojang Studios");
        CAPE_NAMES.put("6390c54ca1e541a75235dadf23555ed2", "Mojang");
        CAPE_NAMES.put("492d8392bf27b659897752dca6de7778", "Realms Mapmaker");
        CAPE_NAMES.put("00f15c80c9ab3540477210d4e58af337", "MineCon 2011");
        CAPE_NAMES.put("2b7ccdbfd1d89520f335822140d83d52", "MineCon 2012");
        CAPE_NAMES.put("4d1709d6e62c99ec7220e0787df0362e", "MineCon 2015");
        CAPE_NAMES.put("de4a8ad0267f4fc0f41a732ebcf10ec9", "MineCon 2016");
        CAPE_NAMES.put("1025a532853c7a29518ffaedda0621d2", "Minecraft Experience");
        CAPE_NAMES.put("4f4363ee4fc7b1b299dd644dc27d182e", "Yearn");
        CAPE_NAMES.put("7f4224b74be5756a78973d2621f85566", "Home");
        CAPE_NAMES.put("5ca285a4bba6c769dff12453b8084235", "MCC 15th Year");
        CAPE_NAMES.put("b69d6840faf88dadc57da2633aed0835", "Menace");
        CAPE_NAMES.put("9af295341c025102c2d9a51b695bd0c4", "Mojang Office");
        CAPE_NAMES.put("719f820d513dc22f5f788a46e8ea3aa7", "Follower's");
        CAPE_NAMES.put("bfd10540b531afbcbec285293d17b358", "Purple Heart");
        CAPE_NAMES.put("b4acb3ba0ca9d89e996928e21fad42c5", "Cherry Blossom");
        CAPE_NAMES.put("21c9cf811d476ae1c4e58f3cceace025", "Vanilla");
        CAPE_NAMES.put("e7447a28eb5d03b01dee9f6710937eb2", "15th Anniversary");
        CAPE_NAMES.put("5b37a01fde6a3e075f3bc5694c18e667", "Migrator");
    }


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
     * @param initialCape the json data to create the cape with (optional)
     * @return the cape
     */
    public Cape getCape(String id, Cape initialCape) {
        Cape cape = this.capeRepository.findById(id).orElse(null);
        if (cape != null && initialCape != null) {
            cape = createCape(initialCape);
            return cape;
        }

        if (cape == null) {
            log.info("Cape {} not found", id);
            return null;
        }

        cape.setName(CAPE_NAMES.getOrDefault(cape.getId(), null));
        return cape;
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

    /**
     * Gets all capes.
     *
     * @return all capes
     */
    public List<Cape> getAllCapes() {
        List<Cape> capes = capeRepository.findAll();
        capes.forEach(cape -> cape.setName(CAPE_NAMES.getOrDefault(cape.getId(), null)));
        return capes;
    }
}
