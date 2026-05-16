package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skin_change_events")
@SQLInsert(sql = "INSERT INTO skin_change_events (player_id, from_skin_id, skin_id, timestamp, id) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")
@Getter
@NoArgsConstructor
public class SkinChangeEventRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skin_change_events_seq")
    @SequenceGenerator(name = "skin_change_events_seq", sequenceName = "skin_change_events_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @Column(name = "from_skin_id")
    private Long fromSkinId;

    @ManyToOne
    @JoinColumn(nullable = false, name = "skin_id")
    private SkinRow skin;

    @Column(nullable = false)
    private Instant timestamp;

    public SkinChangeEventRow(UUID playerId, Long fromSkinId, SkinRow skin, Instant timestamp) {
        this.playerId = playerId;
        this.fromSkinId = fromSkinId;
        this.skin = skin;
        this.timestamp = timestamp;
    }
}