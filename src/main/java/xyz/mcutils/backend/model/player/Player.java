package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.response.SkinResponse;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Document("players")
@Log4j2(topic = "Player")
public class Player {
    /**
     * The UUID of the player
     */
    @Id
    private UUID uniqueId;

    /**
     * The trimmed UUID of the player
     */
    private String trimmedUniqueId;

    /**
     * The username of the player
     */
    @Setter
    private String username;

    /**
     * Is this profile legacy?
     * <p>
     * A "Legacy" profile is a profile that
     * has not yet migrated to a Mojang account.
     * </p>
     */
    @Setter
    private boolean legacyAccount;

    /**
     * The number of uuids contributed by this player
     */
    @Setter
    private int uuidsContributed = 0; // Default to 0

    /**
     * The UUID of the player who contributed
     * this account to be tracked
     */
    @JsonIgnore
    @Setter
    private UUID contributedBy;

    /**
     * The skin of the player, null if the
     * player does not have a skin
     */
    @JsonIgnore
    @Setter
    private String skinId;

    /**
     * The cape of the player, null if the
     * player does not have a cape
     */
    @JsonIgnore
    @Setter
    private String capeId;

    /**
     * The usernames this player has used previously,
     * includes the current skin.
     */
    private List<UsernameHistoryEntry> usernameHistory;

    /**
     * The skins this player has used previously,
     * includes the current skin.
     */
    private List<SkinHistoryEntry> skinHistory;

    /**
     * The capes this player has used previously,
     * includes the current skin.
     */
    private List<CapeHistoryEntry> capes;

    /**
     * The timestamp when this player last had
     * their information (username, skin history, etc...) updated.
     */
    @Setter @JsonProperty("lastRefreshed")
    private long lastUpdated;

    public Player(MojangProfileToken profile) {
        this.uniqueId = UUIDUtils.addDashes(profile.getId());
        this.trimmedUniqueId = UUIDUtils.removeDashes(this.uniqueId);
        this.username = profile.getName();
        this.legacyAccount = profile.isLegacy();

        this.usernameHistory = new ArrayList<>();
        this.skinHistory = new ArrayList<>();
        this.capes = new ArrayList<>();

        // Get the skin and cape
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape();
        Skin skin = skinAndCape.getLeft();
        Cape cape = skinAndCape.getRight();

        if (skin != null) {
            this.skinId = skin.getId();
            SkinService.INSTANCE.createSkin(skin);

            this.skinHistory.add(new SkinHistoryEntry(
                    skin.getId(),
                    -1,
                    -1
            ));
        }

        if (cape != null) {
            this.capeId = cape.getId();
            CapeService.INSTANCE.createCape(cape);

            String[] capeUrlParts = cape.getUrl().split("/");
            this.capes.add(new CapeHistoryEntry(
                    capeUrlParts[capeUrlParts.length - 1],
                    -1,
                    -1
            ));
        }

        this.usernameHistory.add(new UsernameHistoryEntry(
                profile.getName(),
                -1
        ));

        this.lastUpdated = System.currentTimeMillis();
    }

    public Player() {
        if (this.usernameHistory == null) {
            this.usernameHistory = new ArrayList<>();
        }
        if (this.skinHistory == null) {
            this.skinHistory = new ArrayList<>();
        }
        if (this.capes == null) {
            this.capes = new ArrayList<>();
        }
    }

    /**
     * Gets the current cape for the player.
     *
     * @return the cape, or null if they have no cape
     */
    public Cape getCape() {
        return this.capeId != null ? CapeService.INSTANCE.getCape(this.capeId) : null;
    }

    /**
     * Gets the player's skin, if available.
     *
     * @return the player's skin, or null if no skin
     */
    public SkinResponse getSkin() {
        if (this.skinId == null) {
            return null;
        }
        Skin skin = SkinService.INSTANCE.getSkin(this.skinId);
        if (skin == null) {
            return null;
        }

        SkinResponse skinResponse = new SkinResponse(skin);
        skinResponse.populatePartUrls(this.uniqueId.toString());
        return skinResponse;
    }

    /**
     * Gets the time until the player's information is refreshed.
     *
     * @return the time until the player's information is refreshed
     */
    public long getRefreshingIn() {
        return (this.lastUpdated + (24 * 60 * 60 * 1000)) - System.currentTimeMillis();
    }

    /**
     * Updates the player's username history.
     *
     * @param player          the player to update
     * @param currentUsername the current username
     */
    public void updateUsernameHistory(Player player, String currentUsername) {
        List<UsernameHistoryEntry> usernameHistory = player.getUsernameHistory();
        if (usernameHistory.stream().noneMatch(s -> s.getUsername().equals(currentUsername))) {
            usernameHistory.add(new UsernameHistoryEntry(
                    currentUsername,
                    usernameHistory.isEmpty() ? -1 : System.currentTimeMillis()
            ));
        }
    }

    /**
     * Updates the player's skin history.
     *
     * @param player      the player to update
     * @param currentSkin the current skin
     */
    public void updateSkinHistory(Player player, Skin currentSkin) {
        String previousSkinId = player.getSkinId();
        List<SkinHistoryEntry> skinHistory = player.getSkinHistory();

        if (previousSkinId == null || !previousSkinId.equals(currentSkin.getId())) {
            Optional<SkinHistoryEntry> existingEntry = skinHistory.stream()
                    .filter(entry -> entry.getId().equals(currentSkin.getId()))
                    .findFirst();

            long currentTime = System.currentTimeMillis();
            if (existingEntry.isPresent()) {
                existingEntry.get().setLastUsed(currentTime);
            } else {
                skinHistory.add(new SkinHistoryEntry(
                        currentSkin.getId(),
                        currentTime,
                        currentTime
                ));
            }
        }
    }

    /**
     * Updates the player's cape history.
     *
     * @param player      the player to update
     * @param currentCape the current cape
     */
    public void updateCapeHistory(Player player, Cape currentCape) {
        String previousCapeId = player.getCapeId();
        List<CapeHistoryEntry> capes = player.getCapes();

        if (previousCapeId == null || !previousCapeId.equals(currentCape.getId())) {
            Optional<CapeHistoryEntry> existingEntry = capes.stream()
                    .filter(entry -> entry.getId().equals(currentCape.getId()))
                    .findFirst();

            long currentTime = System.currentTimeMillis();
            if (existingEntry.isPresent()) {
                existingEntry.get().setLastUsed(currentTime);
            } else {
                if (currentCape != null) {
                    capes.add(new CapeHistoryEntry(
                            currentCape.getId(),
                            currentTime,
                            currentTime
                    ));
                }
            }
        }
    }

    /**
     * Refreshes the player's information from Mojang.
     *
     * @param cachedPlayer     the cached player to update
     * @param mojangService    the mojang service to use
     * @param playerRepository the player repository to use
     */
    public void refresh(CachedPlayer cachedPlayer, @NonNull MojangService mojangService, @NonNull PlayerRepository playerRepository) {
        MojangProfileToken profileToken = mojangService.getProfile(this.getUniqueId().toString());
        Tuple<Skin, Cape> skinAndCape = profileToken.getSkinAndCape();
        Skin currentSkin = skinAndCape.getLeft();
        Cape currentCape = skinAndCape.getRight();
        String currentUsername = profileToken.getName();

        Player player = cachedPlayer.getPlayer();

        // Update player
        player.updateUsernameHistory(player, currentUsername);

        if (currentSkin != null) {
            player.updateSkinHistory(player, currentSkin);
        }
        if (currentCape != null) {
            player.updateCapeHistory(player, currentCape);
        }

        // Update username if it's different
        if (!currentUsername.equals(player.getUsername())) {
            player.setUsername(currentUsername);
        }

        // Update skin if it's different
        if (currentSkin != null && !currentSkin.getId().equals(player.getSkinId())) {
            player.setSkinId(currentSkin.getId());
        }

        // Update cape if it's different
        if (currentCape != null && !currentCape.getId().equals(player.getCapeId())) {
            player.setCapeId(currentCape.getId());
        }

        // Create the skin if it doesn't exist
        if (currentSkin != null && !SkinService.INSTANCE.skinExists(currentSkin.getId())) {
            SkinService.INSTANCE.createSkin(currentSkin);
        }

        // Create the cape if it doesn't exist
        if (currentCape != null && !CapeService.INSTANCE.capeExists(currentCape.getId())) {
            CapeService.INSTANCE.createCape(currentCape);
        }

        player.setLastUpdated(System.currentTimeMillis());
        playerRepository.save(player);
    }
}
