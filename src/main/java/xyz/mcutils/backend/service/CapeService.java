package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.cache.CachedPlayerCapePart;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.model.cape.CapeData;
import xyz.mcutils.backend.model.cape.CapeRendererType;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.repository.PlayerCapePartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service @Slf4j
public class CapeService {
    public static CapeService INSTANCE;

    private static final Map<String, CapeData> capes = new LinkedHashMap<>();
    static {
        // Missing texture ids (I will give good succ if you find these)
        // Birthday, Oxeye, Blueprint, Valentine, Test

        List<CapeData> capeData = new ArrayList<>();
        capeData.add(new CapeData("Migrator", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"));
        capeData.add(new CapeData("Pan", "28de4a81688ad18b49e735a273e086c18f1e3966956123ccb574034c06f5d336"));
        capeData.add(new CapeData("Home", "1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd"));
        capeData.add(new CapeData("Zombie Horse", "a3f6e4f14801f3ea55e3d95b9b4ef3b5e8802d947f669de93d6ec4b9354a436b"));
        capeData.add(new CapeData("Turtle", "5048ea61566353397247d2b7d946034de926b997d5e66c86483dfb1e031aee95"));
        capeData.add(new CapeData("Cobalt", "ca35c56efe71ed290385f4ab5346a1826b546a54d519e6a3ff01efa01acce81"));
        capeData.add(new CapeData("Minecraft Experience", "7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434"));
        capeData.add(new CapeData("Founder's", "99aba02ef05ec6aa4d42db8ee43796d6cd50e4b2954ab29f0caeb85f96bf52a1"));
        capeData.add(new CapeData("15th Anniversary", "cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625"));
        capeData.add(new CapeData("Common", "5ec930cdd2629c8771655c60eebeb867b4b6559b0e6d3bc71c40c96347fa03f0"));
        capeData.add(new CapeData("Vanilla", "f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4"));
        capeData.add(new CapeData("Purple Heart", "cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde"));
        capeData.add(new CapeData("Follower's", "569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350"));
        capeData.add(new CapeData("Cherry Blossom", "afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb"));
        capeData.add(new CapeData("Menace", "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"));
        capeData.add(new CapeData("Mojang Office", "5c29410057e32abec02d870ecb52ec25fb45ea81e785a7854ae8429d7236ca26"));
        capeData.add(new CapeData("Copper", "5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"));
        capeData.add(new CapeData("Yearn", "308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89"));
        capeData.add(new CapeData("MCC 15th Year", "56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df"));

        // Mojang
        capeData.add(new CapeData("Mojang", "5786fe99be377dfb6858859f926c4dbc995751e91cee373468c5fbf4865e7151"));
        capeData.add(new CapeData("Mojang Studios", "9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7"));
        capeData.add(new CapeData("Scrolls", "3efadf6510961830f9fcc077f19b4daf286d502b5f5aafbd807c7bbffcaca245"));
        capeData.add(new CapeData("Mojang Classic", "8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761"));
        capeData.add(new CapeData("Mojira Moderator", "ae677f7d98ac70a533713518416df4452fe5700365c09cf45d0d156ea9396551"));

        // Realms
        capeData.add(new CapeData("Realms Mapmaker", "17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56"));

        // Minecon
        capeData.add(new CapeData("Minecon 2016", "e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980"));
        capeData.add(new CapeData("Minecon 2015", "b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635"));
        capeData.add(new CapeData("Minecon 2013", "153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da"));
        capeData.add(new CapeData("Minecon 2012", "a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6"));
        capeData.add(new CapeData("Minecon 2011", "953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6"));

        // Translator
        capeData.add(new CapeData("Translator", "1bf91499701404e21bd46b0191d63239a4ef76ebde88d27e4d430ac211df681e"));
        capeData.add(new CapeData("Translator (Chinese)", "2262fb1d24912209490586ecae98aca8500df3eff91f2a07da37ee524e7e3cb6"));
        capeData.add(new CapeData("Translator (Japanese)", "ca29f5dd9e94fb1748203b92e36b66fda80750c87ebc18d6eafdb0e28cc1d05f"));

        // One of a kind
        capeData.add(new CapeData("Prismarine", "d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac"));
        capeData.add(new CapeData("dB", "bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac"));
        capeData.add(new CapeData("Millionth Customer", "70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf"));
        capeData.add(new CapeData("Snowman", "23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe"));
        capeData.add(new CapeData("Spade", "2e002d5e1758e79ba51d08d92a0f3a95119f2f435ae7704916507b6c565a7da8"));

        for (CapeData data : capeData) {
            capes.put(data.textureId(), data);
        }
    }

    @Value("${mc-utils.renderer.cape.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.cape.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.cape.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.cape.limits.max_size}")
    private int maxPartSize;

    private final StorageService minioService;
    private final PlayerService playerService;
    private final PlayerCapePartCacheRepository capePartRepository;

    @Autowired
    public CapeService(StorageService minioService, PlayerService playerService, PlayerCapePartCacheRepository capePartRepository) {
        this.minioService = minioService;
        this.playerService = playerService;
        this.capePartRepository = capePartRepository;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets all the known capes.
     *
     * @return the known capes
     */
    public Map<String, CapeData> getCapes() {
        return Collections.unmodifiableMap(capes);
    }

    /**
     * Gets a Cape from the texture id or the player's name / uuid.
     *
     * @param query the query to search for
     * @return the cape, or null
     */
    public Cape getCapeFromTextureIdOrPlayer(String query) {
        Cape cape;
        // I really have no idea how long their sha-1 string length is
        // a player name can't be more than 16 chars, so just assume it's a texture id
        if (query.length() > 16) {
            cape = Cape.fromId(query);
        } else {
            Player player = this.playerService.getPlayer(query).getPlayer();
            cape = player.getCape();
            if (cape == null) {
                throw new NotFoundException("Player '%s' does not have a cape equipped".formatted(player.getUsername()));
            }
        }
        return cape;
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

    /**
     * Gets a cape image from bytes.
     *
     * @param capeBytes the cape bytes
     * @return the cape image
     */
    public BufferedImage getCapeImage(byte[] capeBytes) throws IOException {
        BufferedImage capeImage = ImageIO.read(new ByteArrayInputStream(capeBytes));
        if (capeImage == null) {
            throw new IllegalStateException("Failed to load cape image");
        }
        return capeImage;
    }

    /**
     * Renders a cape part at the given size.
     *
     * @param cape the cape to render
     * @param typeName the cape part type (e.g. FRONT), see {@link CapeRendererType#getByName(String)}
     * @param size the output size (height; width derived from cape aspect)
     * @return the cached cape part (PNG bytes)
     */
    public CachedPlayerCapePart renderCape(Cape cape, String typeName, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Cape rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid cape part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        CapeRendererType part = CapeRendererType.getByName(typeName);
        if (part == null) {
            throw new BadRequestException("Invalid cape part: '%s'".formatted(typeName));
        }

        String key = "%s-%s-%s".formatted(cape.getId(), part.name(), size);

        log.debug("Getting cape part for cape: {} (part {}, size {})", cape.getId(), typeName, size);

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerCapePart> cache = capePartRepository.findById(key);
            if (cache.isPresent()) {
                log.debug("Got cape part for cape {} from cache in {}ms", cape.getId(), System.currentTimeMillis() - cacheStart);
                return cache.get();
            }
        }

        long renderStart = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(cape, size);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);
        log.debug("Took {}ms to render cape part for cape: {}", System.currentTimeMillis() - renderStart, cape.getId());

        CachedPlayerCapePart capePart = new CachedPlayerCapePart(key, pngBytes);

        if (cacheEnabled) {
            CompletableFuture.runAsync(() -> capePartRepository.save(capePart), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for cape part {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return capePart;
    }
}
