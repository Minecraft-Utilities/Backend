package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A player ever seen on a tracked server. Keyed by player UUID (rename-proof); the username
 * column tracks the latest advertised name.
 */
@Entity
@Table(name = "tracker_player_history")
@IdClass(PlayerHistoryRow.PlayerHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerHistoryRow {

    @Id
    @Column(name = "server_uuid", nullable = false)
    private UUID serverUuid;

    @Id
    @Column(name = "player_uuid", nullable = false)
    private UUID playerUuid;

    @Column(name = "username", nullable = false, length = 16)
    private String username;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "times_seen", nullable = false)
    private int timesSeen;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PlayerHistoryId implements Serializable {
        private UUID serverUuid;
        private UUID playerUuid;
    }
}