package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.SkinService;

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
    private VanillaCape cape;

    /**
     * The player's optifine Cape.
     */
    @Nullable
    private OptifineCape optifineCape;

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
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = profile.getSkinAndCape();
        if (skinAndCape != null) {
            SkinTextureToken skinTextureToken = skinAndCape.left();
            this.skin = SkinService.INSTANCE.getSkin(skinTextureToken.getTextureId(), this);
            // Skin was not cached, create it
            if (this.skin == null) {
                skin = SkinService.INSTANCE.createSkin(skinTextureToken, this);
            }
            CapeTextureToken capeTextureToken = skinAndCape.right();
            if (capeTextureToken != null) {
                this.cape = CapeService.INSTANCE.getCapeByTextureId(capeTextureToken.getTextureId());
            }
        }
        try {
            if (OptifineCape.capeExists(this.username).get() == true) {
                this.optifineCape = new OptifineCape(this.username);
            }
        } catch (Exception ignored) { }
    }
}
