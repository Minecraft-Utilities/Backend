package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.*;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerRow {
    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private boolean legacyAccount;

    @Column(nullable = false, name = "submitted_uuids")
    private long submittedUuids;

    @Column(nullable = false, name = "monthly_views")
    private long monthlyViews;

    @ManyToOne
    @JoinColumn(nullable = false, name = "skin_id")
    private SkinRow skin;

    @ManyToOne
    @JoinColumn(name = "cape_id")
    @Nullable
    private CapeRow cape;

    @Column(nullable = false, name = "change_score")
    private double changeScore;

    @Column(name = "last_changed")
    @Nullable
    private Instant lastChanged;

    @Column(nullable = false)
    private Instant lastUpdated;

    @Column(nullable = false)
    private Instant firstSeen;

    /**
     * Mirrors the ORDER BY expression in {@code PlayerRepository.findPlayersForRefresh}.
     */
    public static double computeRefreshPriorityScore(
            PlayerRow player,
            Instant now,
            double popularityWeight,
            double velocityWeight,
            double urgencyWeight
    ) {
        double popularity = Math.log(Math.max(player.monthlyViews, 1)) * popularityWeight;
        double velocity = player.changeScore * velocityWeight;
        double minutesOverdue = Duration.between(player.lastUpdated, now).toSeconds() / 60.0;
        double urgency = Math.log(1 + minutesOverdue) * urgencyWeight;
        return popularity + velocity + urgency;
    }
}
