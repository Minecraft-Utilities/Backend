package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.response.SkinResponse;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.repository.mongo.history.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.UsernameHistoryRepository;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.MojangService;
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
    @Indexed(background = true)
    private String currentSkinId;

    /**
     * The cape of the player, null if the
     * player does not have a cape
     */
    @JsonIgnore
    @Setter
    private String currentCapeId;

    /**
     * The usernames this player has used previously,
     * includes the current skin.
     */
    @Setter
    @Transient
    private List<UsernameHistoryEntry> usernameHistory;

    /**
     * The skins this player has used previously,
     * includes the current skin.
     */
    @Setter
    @Transient
    private List<SkinHistoryEntry> skinHistory;

    /**
     * The capes this player has used previously,
     * includes the current skin.
     */
    @Setter
    @Transient
    private List<CapeHistoryEntry> capes;

    /**
     * The timestamp when this player last had
     * their information (username, skin history, etc...) updated.
     */
    @Setter
    private long lastRefreshed;

    /**
     * The timestamp of when this player was added to McUtils.
     */
    private long timestampTracked;

    public Player(MojangProfileToken profile, SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository,
                  UsernameHistoryRepository usernameHistoryRepository) {
        this.uniqueId = UUIDUtils.addDashes(profile.getId());
        this.username = profile.getName();
        this.legacyAccount = profile.isLegacy();

        this.usernameHistory = new ArrayList<>();
        this.skinHistory = new ArrayList<>();
        this.capes = new ArrayList<>();

        // Get the skin and cape
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape();
        Skin currentSkin = skinAndCape.getLeft();
        Cape currentCape = skinAndCape.getRight();

        if (currentSkin != null) {
            this.currentSkinId = currentSkin.getId();
            SkinService.INSTANCE.createSkin(currentSkin);

            skinHistoryRepository.insert(new SkinHistoryEntry(
                    UUID.randomUUID(),
                    this.uniqueId,
                    currentSkin.getId(),
                    System.currentTimeMillis(),
                    -1
            ));
        }

        if (currentCape != null) {
            this.currentCapeId = currentCape.getId();
            Cape cape = CapeService.INSTANCE.getCape(currentCape.getId(), currentCape);

            String[] capeUrlParts = currentCape.getUrl().split("/");
            capeHistoryRepository.insert(new CapeHistoryEntry(
                    UUID.randomUUID(),
                    this.uniqueId,
                    capeUrlParts[capeUrlParts.length - 1],
                    System.currentTimeMillis(),
                    -1
            ));

            // Update the cape
            if (cape != null) {
                cape.setAccounts(cape.getAccounts() + 1);
                CapeService.INSTANCE.save(cape);
            }
        }

        usernameHistoryRepository.insert(new UsernameHistoryEntry(
                UUID.randomUUID(),
                this.uniqueId,
                profile.getName(),
                -1
        ));

        this.lastRefreshed = System.currentTimeMillis();
        this.timestampTracked = System.currentTimeMillis();
    }

    public Player() {}

    /**
     * Gets the current cape for the player.
     *
     * @return the cape, or null if they have no cape
     */
    public Cape getCurrentCape() {
        return this.currentCapeId != null ? CapeService.INSTANCE.getCape(this.currentCapeId) : null;
    }

    /**
     * Gets the player's skin, if available.
     *
     * @return the player's skin, or null if no skin
     */
    public SkinResponse getCurrentSkin() {
        if (this.currentSkinId == null) {
            return null;
        }
        Skin skin = SkinService.INSTANCE.getSkin(this.currentSkinId);
        if (skin == null) {
            return null;
        }

        SkinResponse skinResponse = new SkinResponse(skin);
        skinResponse.populatePartUrls(this.uniqueId.toString());
        return skinResponse;
    }

    /**
     * Updates the player's username history.
     *
     * @param player          the player to update
     * @param currentUsername the current username
     */
    public void updateUsernameHistory(Player player, String currentUsername, UsernameHistoryRepository repository) {
        Optional<UsernameHistoryEntry> entry = repository.findByPlayerIdAndUsername(player.getUniqueId(), currentUsername);
        if (entry.isEmpty()) {
            repository.insert(new UsernameHistoryEntry(
                    UUID.randomUUID(),
                    player.getUniqueId(),
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
    public void updateSkinHistory(Player player, Skin currentSkin, SkinHistoryRepository repository) {
        String previousSkinId = player.getCurrentSkinId();

        if (previousSkinId == null || !previousSkinId.equals(currentSkin.getId())) {
            Optional<SkinHistoryEntry> optionalEntry = repository.findByPlayerIdAndSkinId(player.getUniqueId(), previousSkinId);

            long currentTime = System.currentTimeMillis();
            if (optionalEntry.isPresent()) {
                SkinHistoryEntry entry = optionalEntry.get();
                entry.setLastUsed(currentTime);
                repository.save(entry);
            } else {
                repository.insert(new SkinHistoryEntry(
                        UUID.randomUUID(),
                        player.getUniqueId(),
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
    public void updateCapeHistory(Player player, Cape currentCape, CapeHistoryRepository repository) {
        String previousCapeId = player.getCurrentCapeId();

        if (previousCapeId == null || !previousCapeId.equals(currentCape.getId())) {
            Optional<CapeHistoryEntry> optionalEntry = repository.findByPlayerIdAndCapeId(player.getUniqueId(), previousCapeId);

            long currentTime = System.currentTimeMillis();
            if (optionalEntry.isPresent()) {
                CapeHistoryEntry entry = optionalEntry.get();
                entry.setLastUsed(currentTime);
                repository.save(entry);
            } else {
                if (currentCape != null) {
                    repository.insert(new CapeHistoryEntry(
                            UUID.randomUUID(),
                            player.getUniqueId(),
                            currentCape.getId(),
                            currentTime,
                            currentTime
                    ));

                    // Update the cape
                    Cape cape = CapeService.INSTANCE.getCape(currentCape.getId());
                    if (cape != null) {
                        cape.setAccounts(cape.getAccounts() + 1);
                        CapeService.INSTANCE.save(cape);
                    }
                }
            }
        }
    }

    /**
     * Determines if a player should be refreshed based on last update time.
     */
    public boolean shouldRefresh(boolean enableRefresh) {
        return enableRefresh &&
                this.getLastRefreshed() < System.currentTimeMillis() - (3 * 60 * 60 * 1000);
    }

    /**
     * Refreshes the player's information from Mojang.
     *
     * @param mojangService    the mojang service to use
     */
    public void refresh(@NonNull MojangService mojangService, SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository,
                        UsernameHistoryRepository usernameHistoryRepository) {
        MojangProfileToken profileToken = mojangService.getProfile(this.getUniqueId().toString());
        Tuple<Skin, Cape> skinAndCape = profileToken.getSkinAndCape();
        Skin currentSkin = skinAndCape.getLeft();
        Cape currentCape = skinAndCape.getRight();
        String currentUsername = profileToken.getName();

        // Update player
        this.updateUsernameHistory(this, currentUsername, usernameHistoryRepository);

        if (currentSkin != null) {
            this.updateSkinHistory(this, currentSkin, skinHistoryRepository);
        }
        if (currentCape != null) {
            this.updateCapeHistory(this, currentCape, capeHistoryRepository);
        }

        // Update username if it's different
        if (!currentUsername.equals(this.getUsername())) {
            this.setUsername(currentUsername);
        }

        // Update skin if it's different
        if (currentSkin != null && !currentSkin.getId().equals(this.getCurrentSkinId())) {
            this.setCurrentSkinId(currentSkin.getId());
        }

        // Update cape if it's different
        if (currentCape != null && !currentCape.getId().equals(this.getCurrentCapeId())) {
            this.setCurrentCapeId(currentCape.getId());
        }

        // Create the skin if it doesn't exist
        if (currentSkin != null && !SkinService.INSTANCE.skinExists(currentSkin.getId())) {
            SkinService.INSTANCE.createSkin(currentSkin);
        }

        // Create the cape if it doesn't exist
        if (currentCape != null && !CapeService.INSTANCE.capeExists(currentCape.getId())) {
            CapeService.INSTANCE.createCape(currentCape);
        }

        this.setLastRefreshed(System.currentTimeMillis());
    }
}
