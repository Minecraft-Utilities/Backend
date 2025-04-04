package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.PlayerService;

import java.util.*;

@AllArgsConstructor
@Getter @EqualsAndHashCode @ToString
@Document("players")
public class Player {
    /**
     * The UUID of the player
     */
    @Id private UUID uniqueId;

    /**
     * The trimmed UUID of the player
     */
    private String trimmedUniqueId;

    /**
     * The username of the player
     */
    @Setter private String username;

    /**
     * Is this profile legacy?
     * <p>
     * A "Legacy" profile is a profile that
     * has not yet migrated to a Mojang account.
     * </p>
     */
    @Setter private boolean legacyAccount;

    /**
     * The number of uuids contributed by this player
     */
    @Setter private int uuidsContributed = 0; // Default to 0

    /**
     * The UUID of the player who contributed
     * this account to be tracked
     */
    @JsonIgnore @Setter private UUID contributedBy;

    /**
     * The skin of the player, null if the
     * player does not have a skin
     */
    @Setter private Skin skin;

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
    @Setter @JsonIgnore
    private long lastUpdated;

    public Player(MojangProfileToken profile) {
        this.uniqueId = UUIDUtils.addDashes(profile.getId());
        this.trimmedUniqueId = UUIDUtils.removeDashes(this.uniqueId);
        this.username = profile.getName();
        this.legacyAccount = profile.isLegacy();

        // Get the skin and cape
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape();
        this.skin = skinAndCape != null ? skinAndCape.getLeft() : null;

        this.usernameHistory = new ArrayList<>();
        this.usernameHistory.add(new UsernameHistoryEntry(
                profile.getName(),
                -1
        ));

        if (skinAndCape != null) {
            Cape cape = skinAndCape.getRight();
            Skin skin = skinAndCape.getLeft();

            this.skinHistory = new ArrayList<>();
            if (skin != null) {
                this.skinHistory.add(new SkinHistoryEntry(
                        skin.getId(),
                        skin.isLegacy(),
                        skin.getModel(),
                        -1,
                        -1
                ));
            }

            this.capes = new ArrayList<>();
            if (cape != null) {
                String[] capeUrlParts = cape.getUrl().split("/");
                this.capes.add(new CapeHistoryEntry(
                        capeUrlParts[capeUrlParts.length - 1],
                        -1,
                        -1
                ));
            }
        }

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
        this.capes.sort(Comparator.comparingLong(CapeHistoryEntry::getLastUsed).reversed());
        Optional<CapeHistoryEntry> historyEntry = this.capes.stream().findFirst();
        if (historyEntry.isEmpty()) {
            return null;
        }
        CapeHistoryEntry entry = historyEntry.get();
        return new Cape(
                "https://textures.minecraft.net/texture/" + entry.getId(),
                entry.getId()
        );
    }

    /**
     * Updates the player's username history.
     *
     * @param player the player to update
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
     * @param player the player to update
     * @param currentSkin the current skin
     */
    public void updateSkinHistory(Player player, Skin currentSkin) {
        Skin previousSkin = player.getSkin();
        String previousSkinId = previousSkin != null ? previousSkin.getId() : null;
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
                        currentSkin.isLegacy(),
                        currentSkin.getModel(),
                        currentTime,
                        currentTime
                ));
            }
        }
    }

    /**
     * Updates the player's cape history.
     *
     * @param player the player to update
     * @param currentCape the current cape
     */
    public void updateCapeHistory(Player player, Cape currentCape) {
        Cape previousCape = player.getCape();
        String previousCapeId = previousCape != null ? previousCape.getId() : null;
        List<CapeHistoryEntry> capes = player.getCapes();

        if (previousCapeId == null || !previousCapeId.equals(currentCape.getId())) {
            Optional<CapeHistoryEntry> existingEntry = capes.stream()
                    .filter(entry -> entry.getId().equals(currentCape.getId()))
                    .findFirst();

            long currentTime = System.currentTimeMillis();
            if (existingEntry.isPresent()) {
                existingEntry.get().setLastUsed(currentTime);
            } else {
                capes.add(new CapeHistoryEntry(
                        currentCape.getId(),
                        currentTime,
                        currentTime
                ));
            }
        }
    }

    /**
     * Should this player be refreshed?
     *
     * @param fastRefresh whether to refresh fast
     * @return whether to refresh
     */
    public boolean shouldRefresh(boolean fastRefresh) {
        if (fastRefresh && this.lastUpdated < System.currentTimeMillis() - (6 * 60 * 60 * 1000)) { // 6 hours ago or more
            return true;
        } else return !fastRefresh && this.lastUpdated < System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours ago or more
    }

    /**
     * Refreshes the player's information from Mojang.
     *
     * @param mojangService the mojang service to use
     * @param playerService the player service to use
     * @param playerRepository the player repository to use
     * @param fastRefresh whether to fast refresh the player information (6 hours instead of 24 hours)
     */
    public void refresh(@NonNull MojangService mojangService, @NonNull PlayerService playerService,
                        @NonNull PlayerRepository playerRepository, boolean fastRefresh) {
        if (!this.shouldRefresh(fastRefresh)) {
            return;
        }
        this.lastUpdated = System.currentTimeMillis();

        MojangProfileToken profileToken = mojangService.getProfile(this.getUniqueId().toString());
        Tuple<Skin, Cape> skinAndCape = profileToken.getSkinAndCape();
        Skin currentSkin = skinAndCape.getLeft();
        Cape currentCape = skinAndCape.getRight();
        String currentUsername = profileToken.getName();

        CachedPlayer cachedPlayer = playerService.getCachedPlayer(this.getUniqueId().toString());
        Player player = cachedPlayer.getPlayer();

        // Update player
        player.updateUsernameHistory(player, currentUsername);
        player.updateSkinHistory(player, currentSkin);
        player.updateCapeHistory(player, currentCape);

        // Update username if it's different
        if (!currentUsername.equals(player.getUsername())) {
            player.setUsername(currentUsername);
        }

        // Update skin if it's different
        if (currentSkin != null && !currentSkin.equals(player.getSkin())) {
            player.setSkin(currentSkin);
        }

        player.setLastUpdated(System.currentTimeMillis());
        playerRepository.save(player);
    }
}
