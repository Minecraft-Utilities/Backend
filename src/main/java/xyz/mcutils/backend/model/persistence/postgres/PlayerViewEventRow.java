package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_view_events")
@Getter
@NoArgsConstructor
public class PlayerViewEventRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private UUID playerId;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Instant viewedAt;

    public PlayerViewEventRow(UUID playerId, String ipAddress, Instant viewedAt) {
        this.playerId = playerId;
        this.ipAddress = ipAddress;
        this.viewedAt = viewedAt;
    }
}