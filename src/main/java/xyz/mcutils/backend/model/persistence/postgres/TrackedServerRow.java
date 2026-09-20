package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A Minecraft server tracked by the internet server tracker: per-server telemetry captured
 * by discovery verifications and refresh-cycle pings.
 */
@Entity
@Table(name = "tracker_servers")
@Getter
@Setter
@NoArgsConstructor
public class TrackedServerRow {

    /**
     * Surrogate id generated on first sight; the status protocol advertises no server id.
     */
    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    /**
     * The last successful status fetch.
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "online_count", nullable = false)
    private int onlineCount;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "protocol")
    private Integer protocol;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "motd")
    private String motd;

    @Column(name = "motd_hash")
    private byte[] motdHash;

    @Column(name = "favicon_hash")
    private byte[] faviconHash;

    @Column(name = "modded", nullable = false)
    private boolean modded;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "prevents_chat_reports", nullable = false)
    private boolean preventsChatReports;

    @Column(name = "enforces_secure_chat", nullable = false)
    private boolean enforcesSecureChat;

    @Column(name = "previews_chat", nullable = false)
    private boolean previewsChat;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "asn")
    private Long asn;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "honeypot", nullable = false)
    private boolean honeypot;

    @Column(name = "consecutive_offline", nullable = false)
    private int consecutiveOffline;

    /**
     * Last refresh attempt; drives the equal-cadence refresh cycle ({@code epoch} = refreshable
     * immediately).
     */
    @Column(name = "last_refreshed", nullable = false)
    private Instant lastRefreshed;
}