package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.history.CapeHistory;
import xyz.mcutils.backend.model.domain.player.history.SkinHistory;
import xyz.mcutils.backend.model.domain.player.history.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.PlayerService;

import java.time.Instant;
import java.util.Set;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FullPlayer extends BasicPlayer {
    /**
     * Is this profile legacy?
     * <p>
     * A "Legacy" profile is a profile that
     * has not yet migrated to a Mojang account.
     * </p>
     */
    private boolean legacyAccount;

    /**
     * The amount of new uuids this player has submitted.
     */
    private long submittedUuids;

    /**
     * The skins this player has previously equipped (including current).
     */
    private Set<SkinHistory> skinHistory;

    /**
     * The capes this player has previously equipped (including current).
     */
    @Nullable
    private Set<CapeHistory> capeHistory;

    /**
     * The username history of the player.
     */
    @Nullable
    private Set<UsernameHistory> usernameHistory;

    /**
     * The time this account was last updated.
     */
    private Instant lastUpdated;

    public static FullPlayer fromRow(PlayerRow playerRow, PlayerService playerService) {
        CapeRow cape = playerRow.getCape();
        return FullPlayer.builder()
                .uniqueId(playerRow.getId())
                .username(playerRow.getUsername())
                .skin(Skin.fromRow(playerRow.getSkin()))
                .cape(cape != null ? VanillaCape.fromRow(cape) : null)
                .firstSeen(playerRow.getFirstSeen())
                .legacyAccount(playerRow.isLegacyAccount())
                .submittedUuids(playerRow.getSubmittedUuids())
                .skinHistory(playerService.getSkinHistory(playerRow))
                .capeHistory(playerService.getCapeHistory(playerRow))
                .usernameHistory(playerService.getUsernameHistory(playerRow))
                .lastUpdated(playerRow.getLastUpdated())
                .build();
    }
}
