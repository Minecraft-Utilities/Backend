package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
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
    private List<Skin> skinHistory;

    /**
     * The Cape for the player.
     */
    @Nullable
    private VanillaCape cape;

    /**
     * The capes this player has previously equipped (including current).
     */
    @Nullable
    private List<VanillaCape> capeHistory;

    /**
     * The player's optifine Cape.
     */
    @Nullable
    private OptifineCape optifineCape;

    public Player(UUID uniqueId, String username, boolean legacyAccount, Skin skin, List<Skin> skinHistory, @Nullable VanillaCape cape, @Nullable List<VanillaCape> capeHistory) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.legacyAccount = legacyAccount;
        this.skin = skin;
        this.skinHistory = skinHistory;
        this.cape = cape;
        this.capeHistory = capeHistory;

        try {
            if (OptifineCape.capeExists(this.username).get() == true) {
                this.optifineCape = new OptifineCape(this.username);
            }
        } catch (Exception ignored) { }
    }

    public Skin getSkin() {
        if (skin == null) {
            return null;
        }

        skin.updateParts(this);
        return skin;
    }
}
