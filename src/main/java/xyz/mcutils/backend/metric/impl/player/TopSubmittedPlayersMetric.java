package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

import java.util.List;

/**
 * Exposes the top 10 players by total submitted UUIDs as a gauge, read directly from MongoDB.
 * Use {@code topk(10, top_submitted_players_submitted_uuids)} in Grafana.
 */
public class TopSubmittedPlayersMetric extends Metric<TopSubmittedPlayersMetric.Holder> {

    public TopSubmittedPlayersMetric(MongoTemplate mongoTemplate) {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("top_submitted_players_submitted_uuids")
                        .help("Submitted UUID count for the top 10 players by submission count")
                        .labelNames("username")
                        .callback(callback -> {
                            Query query = new Query()
                                    .with(Sort.by(Sort.Direction.DESC, "submittedUuids"))
                                    .limit(10);
                            query.fields().include("username").include("submittedUuids");
                            List<Document> results = mongoTemplate.find(query, Document.class, "players");
                            for (Document doc : results) {
                                String username = doc.getString("username");
                                Number count = doc.get("submittedUuids", Number.class);
                                if (username != null && count != null) {
                                    callback.call(count.doubleValue(), username);
                                }
                            }
                        })
                        .register(MetricService.REGISTRY)
        ));
    }

    public record Holder(GaugeWithCallback gauge) {}
}
