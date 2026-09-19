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
 * One online-count sample of a tracked server (popularity/uptime time series).
 */
@Entity
@Table(name = "server_online_history")
@IdClass(ServerOnlineHistoryRow.ServerOnlineHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServerOnlineHistoryRow {

    @Id
    @Column(name = "server_uuid", nullable = false)
    private UUID serverUuid;

    @Id
    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "online", nullable = false)
    private int online;

    @Column(name = "max", nullable = false)
    private int max;

    @Column(name = "version", length = 64)
    private String version;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ServerOnlineHistoryId implements Serializable {
        private UUID serverUuid;
        private Instant sampledAt;
    }
}