package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifies harvested player samples against known player identity before anything is persisted
 * or enqueued: a sample entry {@code (name, uuid)} is only kept when the players table — the
 * identity store, populated exclusively from Mojang-verified profiles — already knows that
 * uuid under that name. Honeypot farms fabricate plausible version-4 UUIDs and usernames that
 * pass the structural honeypot filters; a known, name-matching row is ground truth at zero
 * API cost.
 * <p>
 * Cache-only by design: no Mojang call happens here — the submit pipeline owns the only
 * profile lookup, and this gate must not duplicate it. Entries with a different stored name
 * are dropped as {@code fake_identity}; entries the table does not know cannot be verified and
 * are dropped as {@code unverified}. A player who renamed since the sample is dropped too —
 * the pair no longer matches the stored identity, an accepted tradeoff that self-heals on the
 * next sample under the new name.
 */
@Component
@Slf4j
public class PlayerSampleVerifier {

    private static final String DROP_REASON_FAKE_IDENTITY = "fake_identity";
    private static final String DROP_REASON_UNVERIFIED = "unverified";

    private final PlayerService playerService;
    private final boolean enabled;

    public PlayerSampleVerifier(
            PlayerService playerService,
            @Value("${mc-utils.server-tracker.sample-verify.enabled:true}") boolean enabled
    ) {
        this.playerService = playerService;
        this.enabled = enabled;
    }

    /**
     * @return the entries whose (name, uuid) pair matches the known player identity; every
     * rejected entry records a drop reason on the tracker metric
     */
    public List<HoneypotDetector.SampleEntry> verify(List<HoneypotDetector.SampleEntry> entries) {
        if (!enabled || entries.isEmpty()) {
            return entries;
        }
        ServerTrackerMetric metrics = MetricService.getMetric(ServerTrackerMetric.class);
        List<HoneypotDetector.SampleEntry> verified = new ArrayList<>(entries.size());
        for (HoneypotDetector.SampleEntry entry : entries) {
            Optional<PlayerRow> known = playerService.getCachedPlayer(entry.uuid().toString());
            if (known.isPresent()) {
                if (known.get().getUsername().equalsIgnoreCase(entry.name())) {
                    verified.add(entry);
                } else {
                    drop(metrics, DROP_REASON_FAKE_IDENTITY);
                }
            } else {
                drop(metrics, DROP_REASON_UNVERIFIED);
            }
        }
        return verified;
    }

    private static void drop(ServerTrackerMetric metrics, String reason) {
        if (metrics != null) {
            metrics.recordSampleDropped(reason);
        }
    }
}