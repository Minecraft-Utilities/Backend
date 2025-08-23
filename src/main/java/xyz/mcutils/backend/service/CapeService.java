package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Log4j2(topic = "Cape Service")
public class CapeService {
    public static CapeService INSTANCE;
    public static final Map<String, String> CAPE_NAMES = new HashMap<>();
    static {
        // One of a kind
        CAPE_NAMES.put("d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac", "Prismarine");
        CAPE_NAMES.put("bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac", "dB");
        CAPE_NAMES.put("70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf", "Millionth Customer");
        CAPE_NAMES.put("23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe", "Snowman");
        CAPE_NAMES.put("04ddcbd316ec68364238697174fb1c61", "Birthday");

        // Realms
        CAPE_NAMES.put("953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6", "MineCon 2011");
        CAPE_NAMES.put("a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6", "MineCon 2012");
        CAPE_NAMES.put("153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da", "MineCon 2013");
        CAPE_NAMES.put("b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635", "MineCon 2015");
        CAPE_NAMES.put("e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980", "MineCon 2016");

        // Translators
        CAPE_NAMES.put("a944bd437bb6d12822f740c55ee1a3c9", "Translator");
        CAPE_NAMES.put("008daa52d7b3e61b686f7cafa3680c0f", "Translator (Japanese)");
        CAPE_NAMES.put("a2b113cab4c553b9b306f25e2d599513", "Translator (Chinese)");

        // Mojang
        CAPE_NAMES.put("8fb7780634ea6c0729fea7a7e6f50c28", "Mojang Studios");
        CAPE_NAMES.put("6390c54ca1e541a75235dadf23555ed2", "Mojang");
        CAPE_NAMES.put("8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761", "Mojang (Classic)");

        // Mojira
        CAPE_NAMES.put("172e4e9fd6c0afdf086b8bdb58c0bad5", "Mojira Moderator");

        // Minecraft Movie
        CAPE_NAMES.put("308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89", "Yearn");
        CAPE_NAMES.put("1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd", "Home");
        CAPE_NAMES.put("dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1", "Menace");

        // Realms
        CAPE_NAMES.put("17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56", "Realms Mapmaker");

        CAPE_NAMES.put("f0b8db3ae2d2857c6d262fcbc2aa4737", "Spade");
        CAPE_NAMES.put("c7091e237e7562e73990a97dda5bc554", "Test");
        CAPE_NAMES.put("22923c6efb6deee96bd7ecd87086bbb8", "Turtle");
        CAPE_NAMES.put("bfa8cc9d7af57fd856890af7c2bf2a4a", "Valentine");
        CAPE_NAMES.put("1044b1f5dfc2626b573d8d74a9307a7f", "Scrolls");
        CAPE_NAMES.put("bd64503808bc49c8d80e327754894d4f", "Cobalt");
        CAPE_NAMES.put("7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434", "Minecraft Experience");
        CAPE_NAMES.put("56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df", "MCC 15th Year");
        CAPE_NAMES.put("5c29410057e32abec02d870ecb52ec25fb45ea81e785a7854ae8429d7236ca26", "Mojang Office");
        CAPE_NAMES.put("569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350", "Follower's");
        CAPE_NAMES.put("cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde", "Purple Heart");
        CAPE_NAMES.put("afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb", "Cherry Blossom");
        CAPE_NAMES.put("f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4", "Vanilla");
        CAPE_NAMES.put("cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625", "15th Anniversary");
        CAPE_NAMES.put("2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933", "Migrator");
    }


    private final CapeRepository capeRepository;
    private final PlayerRepository playerRepository;

    @Autowired
    public CapeService(CapeRepository capeRepository, PlayerRepository playerRepository) {
        this.capeRepository = capeRepository;
        this.playerRepository = playerRepository;
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
        if (cape == null && initialCape != null) {
            cape = createCape(initialCape);
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
     * Saves a cape.
     *
     * @param cape the cape to save
     */
    public void save(Cape cape) {
        this.capeRepository.save(cape);
    }

    /**
     * Gets all capes from the database.
     *
     * @param addUsage adds the amount of accounts using each cape
     * @return all capes
     */
    public List<Cape> getAllCapes(boolean addUsage) {
        List<Cape> capes = capeRepository.findAll();
        
        // Set names for all capes
        capes.forEach(cape -> cape.setName(CAPE_NAMES.getOrDefault(cape.getId(), null)));

        // Add per cape account usage
        if (addUsage) {
            List<CompletableFuture<Void>> futures = capes.stream()
                .map(cape -> CompletableFuture.runAsync(() -> 
                    cape.setAccounts(playerRepository.countByCurrentCapeId(cape.getId()))
                ))
                .toList();
            
            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        return capes.stream().sorted(Comparator.comparing(Cape::getAccounts).reversed()).toList();
    }
}
