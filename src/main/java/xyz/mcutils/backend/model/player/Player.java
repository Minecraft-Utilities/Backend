package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;

import java.util.*;

@AllArgsConstructor @NoArgsConstructor
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

        this.usernameHistory = new ArrayList<>();
        this.usernameHistory.add(new UsernameHistoryEntry(
                profile.getName(),
                -1
        ));

        // Get the skin and cape
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape();
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

    /**
     * Gets the current username for the player.
     *
     * @return the username
     */
    public String getUsername() {
        if (this.usernameHistory == null) {
            this.usernameHistory = new ArrayList<>();
        }
        this.usernameHistory.sort(Comparator.comparingLong(UsernameHistoryEntry::getTimestamp).reversed());
        Optional<UsernameHistoryEntry> historyEntry = this.usernameHistory.stream().findFirst();
        return historyEntry.map(UsernameHistoryEntry::getUsername).orElse(null);
    }

    /**
     * Gets the current skin for the player.
     *
     * @return the skin, or null if they have no skin
     */
    public Skin getSkin() {
        if (this.skinHistory == null) {
            this.skinHistory = new ArrayList<>();
        }
        this.skinHistory.sort(Comparator.comparingLong(SkinHistoryEntry::getLastUsed).reversed());
        Optional<SkinHistoryEntry> historyEntry = this.skinHistory.stream().findFirst();
        if (historyEntry.isEmpty()) {
            return null;
        }
        SkinHistoryEntry entry = historyEntry.get();
        Skin skin = new Skin(
                "http://textures.minecraft.net/texture/" + entry.getId(),
                entry.getModel()
        );
        skin.populatePartUrls(String.valueOf(this.uniqueId));
        return skin;
    }

    /**
     * Gets the current cape for the player.
     *
     * @return the cape, or null if they have no cape
     */
    public Cape getCape() {
        if (this.capes == null) {
            this.capes = new ArrayList<>();
        }
        this.capes.sort(Comparator.comparingLong(CapeHistoryEntry::getLastUsed).reversed());
        Optional<CapeHistoryEntry> historyEntry = this.capes.stream().findFirst();
        if (historyEntry.isEmpty()) {
            return null;
        }
        CapeHistoryEntry entry = historyEntry.get();
        return new Cape(
                "http://textures.minecraft.net/texture/" + entry.getId(),
                entry.getId()
        );
    }
}
