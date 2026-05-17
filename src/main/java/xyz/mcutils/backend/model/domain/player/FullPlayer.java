package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
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

    private long monthlyViews;
    private Set<Skin> skinHistory;
    @Nullable
    private Set<VanillaCape> capeHistory;
    @Nullable
    private Set<UsernameHistory> usernameHistory;
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
                .monthlyViews(playerRow.getMonthlyViews())
                .skinHistory(playerService.getSkinHistory(playerRow))
                .capeHistory(playerService.getCapeHistory(playerRow))
                .usernameHistory(playerService.getUsernameHistory(playerRow))
                .lastUpdated(playerRow.getLastUpdated())
                .build();
    }
}
