package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.response.SkinResponse;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.SkinService;

import java.util.*;

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
    private String skinId;

    /**
     * The cape of the player, null if the
     * player does not have a cape
     */
    @JsonIgnore
    @Setter
    private String capeId;

    /**
     * The views of the player in the last 30 days for each day.
     */
    @JsonIgnore
    private Map<String, Integer> dailyViews = new HashMap<>();

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
            this.skinId = currentSkin.getId();
            SkinService.INSTANCE.createSkin(currentSkin);

            this.skinHistory.add(new SkinHistoryEntry(
                    currentSkin.getId(),
                    -1,
                    -1
            ));
        }

        if (currentCape != null) {
            this.capeId = currentCape.getId();
            Cape cape = CapeService.INSTANCE.getCape(currentCape.getId(), currentCape);

            String[] capeUrlParts = currentCape.getUrl().split("/");
            this.capes.add(new CapeHistoryEntry(
                    capeUrlParts[capeUrlParts.length - 1],
                    -1,
                    -1
            ));

            // Update the cape
            if (cape != null) {
                cape.setAccounts(cape.getAccounts() + 1);
                CapeService.INSTANCE.save(cape);
            }
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
    public Cape getCurrentCape() {
        return this.capeId != null ? CapeService.INSTANCE.getCape(this.capeId) : null;
    }

    /**
     * Gets the player's skin, if available.
     *
     * @return the player's skin, or null if no skin
     */
    public SkinResponse getCurrentSkin() {
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
                this.getLastUpdated() < System.currentTimeMillis() - (3 * 60 * 60 * 1000);
    }

    /**
     * Refreshes the player's information from Mojang.
     *
     * @param mojangService    the mojang service to use
     */
    public void refresh(@NonNull MojangService mojangService) {
        MojangProfileToken profileToken = mojangService.getProfile(this.getUniqueId().toString());
        Tuple<Skin, Cape> skinAndCape = profileToken.getSkinAndCape();
        Skin currentSkin = skinAndCape.getLeft();
        Cape currentCape = skinAndCape.getRight();
        String currentUsername = profileToken.getName();

        // Update player
        this.updateUsernameHistory(this, currentUsername);

        if (currentSkin != null) {
            this.updateSkinHistory(this, currentSkin);
        }
        if (currentCape != null) {
            this.updateCapeHistory(this, currentCape);
        }

        // Update username if it's different
        if (!currentUsername.equals(this.getUsername())) {
            this.setUsername(currentUsername);
        }

        // Update skin if it's different
        if (currentSkin != null && !currentSkin.getId().equals(this.getSkinId())) {
            this.setSkinId(currentSkin.getId());
        }

        // Update cape if it's different
        if (currentCape != null && !currentCape.getId().equals(this.getCapeId())) {
            this.setCapeId(currentCape.getId());
        }

        // Create the skin if it doesn't exist
        if (currentSkin != null && !SkinService.INSTANCE.skinExists(currentSkin.getId())) {
            SkinService.INSTANCE.createSkin(currentSkin);
        }

        // Create the cape if it doesn't exist
        if (currentCape != null && !CapeService.INSTANCE.capeExists(currentCape.getId())) {
            CapeService.INSTANCE.createCape(currentCape);
        }

        this.setLastUpdated(System.currentTimeMillis());
    }
}
