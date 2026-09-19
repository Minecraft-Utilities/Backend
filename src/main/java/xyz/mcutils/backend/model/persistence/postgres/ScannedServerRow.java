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

/**
 * A Minecraft server discovered by the internet scanner.
 */
@Entity
@Table(name = "scanned_servers")
@IdClass(ScannedServerRow.ScannedServerId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScannedServerRow {

    @Id
    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Id
    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "honeypot", nullable = false)
    private boolean honeypot;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ScannedServerId implements Serializable {
        private String ip;
        private int port;
    }
}