package xyz.mcutils.backend.model.player;

import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;

import java.util.UUID;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
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
     * The Skin for the player.
     */
    private Skin skin;

    /**
     * The Cape for the player.
     */
    private Cape cape;

    /**
     * The raw properties of the player
     */
    private MojangProfileToken.ProfileProperty[] rawProperties;

    public Player(MojangProfileToken profile) {
        this.uniqueId = UUIDUtils.addDashes(profile.getId());
        this.username = profile.getName();
        this.legacyAccount = profile.isLegacy();

        this.rawProperties = profile.getProperties();

        // Get the skin and cape
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape();
        if (skinAndCape != null) {
            this.skin = skinAndCape.getLeft();
            this.skin.populateSkinData(this);
            this.cape = skinAndCape.getRight();
        }
    }

    public Player() {}
}
