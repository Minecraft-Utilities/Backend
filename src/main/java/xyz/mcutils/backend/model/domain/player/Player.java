package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
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
     * The amount of new uuids this player has submitted.
     */
    private long submittedUuids;

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

    /**
     * The date this player was first seen on.
     */
    private Instant firstSeen;

    public static Player fromRow(PlayerRow playerRow, PlayerService playerService) {
        CapeRow cape = playerRow.getCape();
        return new Player(
                playerRow.getId(),
                playerRow.getUsername(),
                playerRow.isLegacyAccount(),
                playerRow.getSubmittedUuids(),
                Skin.fromRow(playerRow.getSkin()),
                cape != null ? VanillaCape.fromRow(cape) : null,
                playerService.getSkinHistory(playerRow),
                playerService.getCapeHistory(playerRow),
                playerService.getUsernameHistory(playerRow),
                playerRow.getLastUpdated(),
                playerRow.getFirstSeen()
        );
    }
}
