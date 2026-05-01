package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Counter for tracked player accounts successfully refreshed.
 * Use with rate() for ETA: tracked_players / (rate(accounts_updated_total[1m]) * 60)
 */
public class AccountsUpdatedMetric extends Metric<AccountsUpdatedMetric.Holder> {

    public AccountsUpdatedMetric() {
        super(new Holder(Counter.builder().name("accounts_updated_total").help("Total number of tracked player accounts successfully refreshed").register(MetricService.REGISTRY)));
    }

    /**
     * Increments the counter by the given number of accounts updated.
     */
    public void inc(long n) {
        if (n > 0) {
            getValue().counter.inc(n);
        }
    }

    public record Holder(Counter counter) {}
}
