package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Singleton row (id = 1) tracking the scanner's position in the IPv4 space and its root seed,
 * so a restart resumes without re-scanning completed /16s.
 */
@Entity
@Table(name = "scan_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanProgressRow {

    public static final int SINGLETON_ID = 1;

    public enum State {
        RUNNING, PAUSED, COMPLETED
    }

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Column(name = "permuted16_pos", nullable = false)
    private int permuted16Pos;

    @Column(name = "offset24", nullable = false)
    private int offset24;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}