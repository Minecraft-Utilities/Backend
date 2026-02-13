package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Slf4j
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
     * The Skin for the player.
     */
    private Skin skin;

    /**
     * The skins this player has previously equipped (including current).
     */
    private Set<Skin> skinHistory;

    /**
     * The Cape for the player.
     */
    @Nullable
    private VanillaCape cape;

    /**
     * The capes this player has previously equipped (including current).
     */
    @Nullable
    private Set<VanillaCape> capeHistory;

    /**
     * The player's optifine Cape.
     */
    @Nullable
    private OptifineCape optifineCape;

    /**
     * The username history of the player.
     */
    @Nullable
    private Set<UsernameHistory> usernameHistory;

    /**
     * The time this account was last updated.
     */
    private Date lastUpdated;

    /**
     * The date this player was first seen on.
     */
    private Date firstSeen;

    public Player(UUID uniqueId, String username, boolean legacyAccount, Skin skin, Set<Skin> skinHistory, @Nullable VanillaCape cape, @Nullable Set<VanillaCape> capeHistory,
                  boolean hasOptifineCape, Set<UsernameHistory> usernameHistory, Date lastUpdated, Date firstSeen) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.legacyAccount = legacyAccount;
        this.skin = skin;
        this.skinHistory = skinHistory;
        this.cape = cape;
        this.capeHistory = capeHistory;
        this.usernameHistory = usernameHistory;
        this.lastUpdated = lastUpdated;
        this.firstSeen = firstSeen;
        this.optifineCape = hasOptifineCape ? new OptifineCape(this.username) : null;
    }
}
