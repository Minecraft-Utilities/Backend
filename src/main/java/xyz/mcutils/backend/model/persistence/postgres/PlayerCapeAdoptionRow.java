package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_cape_adoptions")
@IdClass(PlayerCapeAdoptionId.class)
@Getter
@NoArgsConstructor
public class PlayerCapeAdoptionRow {
    @Id
    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @Id
    @Column(nullable = false, name = "cape_id")
    private long capeId;

    /**
     * Read-only association for entity navigation; insert/update is driven by {@code capeId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cape_id", insertable = false, updatable = false)
    private CapeRow cape;

    @Column(nullable = false, name = "first_seen")
    private Instant firstSeen;

    @Column(name = "last_equipped_at")
    @Nullable
    private Instant lastEquippedAt;

    public PlayerCapeAdoptionRow(UUID playerId, CapeRow cape, Instant firstSeen, Instant lastEquippedAt) {
        this.playerId = playerId;
        this.capeId = cape.getId();
        this.cape = cape;
        this.firstSeen = firstSeen;
        this.lastEquippedAt = lastEquippedAt;
    }
}
