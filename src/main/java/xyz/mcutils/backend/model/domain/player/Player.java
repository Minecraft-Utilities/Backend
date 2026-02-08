package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;

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
     * The Cape for the player.
     */
    @Nullable
    private VanillaCape cape;

    /**
     * The player's optifine Cape.
     */
    @Nullable
    private OptifineCape optifineCape;

    public Player(UUID uniqueId, String username, boolean legacyAccount, Skin skin, @Nullable VanillaCape cape) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.legacyAccount = legacyAccount;
        this.skin = skin;
        this.cape = cape;

        try {
            if (OptifineCape.capeExists(this.username).get() == true) {
                this.optifineCape = new OptifineCape(this.username);
            }
        } catch (Exception ignored) { }
    }
}
