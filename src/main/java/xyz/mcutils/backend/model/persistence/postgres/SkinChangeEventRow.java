package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skin_change_events")
@Getter
@NoArgsConstructor
public class SkinChangeEventRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skin_change_events_seq")
    @SequenceGenerator(name = "skin_change_events_seq", sequenceName = "skin_change_events_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_skin_id")
    private SkinRow fromSkin;

    @ManyToOne
    @JoinColumn(nullable = false, name = "skin_id")
    private SkinRow skin;

    @Column(nullable = false)
    private Instant timestamp;

    public SkinChangeEventRow(UUID playerId, SkinRow fromSkin, SkinRow skin, Instant timestamp) {
        this.playerId = playerId;
        this.fromSkin = fromSkin;
        this.skin = skin;
        this.timestamp = timestamp;
    }
}