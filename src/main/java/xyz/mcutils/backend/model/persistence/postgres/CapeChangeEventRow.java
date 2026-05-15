package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cape_change_events")
@SQLInsert(sql = "INSERT INTO cape_change_events (player_id, cape_id, timestamp, id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")
@Getter
@NoArgsConstructor
public class CapeChangeEventRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cape_change_events_seq")
    @SequenceGenerator(name = "cape_change_events_seq", sequenceName = "cape_change_events_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @ManyToOne
    @JoinColumn(nullable = false, name = "cape_id")
    private CapeRow cape;

    @Column(nullable = false)
    private Instant timestamp;

    public CapeChangeEventRow(UUID playerId, CapeRow cape, Instant timestamp) {
        this.playerId = playerId;
        this.cape = cape;
        this.timestamp = timestamp;
    }
}
