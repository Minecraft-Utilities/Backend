package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_skin_adoptions")
@IdClass(PlayerSkinAdoptionId.class)
@Getter
@NoArgsConstructor
public class PlayerSkinAdoptionRow {
    @Id
    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @Id
    @Column(nullable = false, name = "skin_id")
    private long skinId;

    /**
     * Read-only association for entity navigation; insert/update is driven by {@code skinId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skin_id", insertable = false, updatable = false)
    private SkinRow skin;

    @Column(nullable = false, name = "first_seen")
    private Instant firstSeen;

    @Column(nullable = false, name = "last_equipped_at")
    private Instant lastEquippedAt;

    public PlayerSkinAdoptionRow(UUID playerId, SkinRow skin, Instant firstSeen, Instant lastEquippedAt) {
        this.playerId = playerId;
        this.skinId = skin.getId();
        this.skin = skin;
        this.firstSeen = firstSeen;
        this.lastEquippedAt = lastEquippedAt;
    }
}
