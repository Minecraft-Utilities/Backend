package xyz.mcutils.backend.model.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
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
        Tuple<Skin, Cape> skinAndCape = profile.getSkinAndCape(this);
        if (skinAndCape != null) {
            this.skin = skinAndCape.left();
            this.cape = skinAndCape.right();
        }
    }
}
