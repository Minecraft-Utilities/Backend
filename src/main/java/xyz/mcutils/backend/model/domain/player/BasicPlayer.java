package xyz.mcutils.backend.model.domain.player;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;

import java.time.Instant;
import java.util.UUID;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class BasicPlayer {
    /**
     * The UUID of the player.
     */
    @Id
    private UUID uniqueId;

    /**
     * The username of the player.
     */
    private String username;

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
     * The date this player was first seen on.
     */
    private Instant firstSeen;

    public static BasicPlayer fromRow(PlayerRow row) {
        CapeRow cape = row.getCape();
        return BasicPlayer.builder()
                .uniqueId(row.getId())
                .username(row.getUsername())
                .skin(Skin.fromRow(row.getSkin()))
                .cape(cape != null ? VanillaCape.fromRow(cape) : null)
                .firstSeen(row.getFirstSeen())
                .build();
    }

    public static BasicPlayer from(FullPlayer full) {
        return BasicPlayer.builder()
                .uniqueId(full.getUniqueId())
                .username(full.getUsername())
                .skin(full.getSkin())
                .cape(full.getCape())
                .firstSeen(full.getFirstSeen())
                .build();
    }
}
