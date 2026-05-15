package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "username_change_events")
@SQLInsert(sql = "INSERT INTO username_change_events (new_username, player_id, previous_username, timestamp, id) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")
@Getter
@NoArgsConstructor
public class UsernameChangeEventRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "username_change_events_seq")
    @SequenceGenerator(name = "username_change_events_seq", sequenceName = "username_change_events_seq", allocationSize = 50)
    private long id;

    @Column(nullable = false, name = "player_id")
    private UUID playerId;

    @Column(nullable = false, name = "new_username")
    private String newUsername;

    @Column(name = "previous_username")
    private String previousUsername;

    @Column(nullable = false)
    private Instant timestamp;

    public UsernameChangeEventRow(UUID playerId, String newUsername, String previousUsername, Instant timestamp) {
        this.playerId = playerId;
        this.newUsername = newUsername;
        this.previousUsername = previousUsername;
        this.timestamp = timestamp;
    }
}
