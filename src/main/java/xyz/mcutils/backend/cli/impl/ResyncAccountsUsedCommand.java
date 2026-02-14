package xyz.mcutils.backend.cli.impl;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;

import java.util.List;
import java.util.UUID;

/**
 * CLI command to re-sync cape and skin accounts used/owned counts from the
 * players collection.
 */
@Command(
        name = "resync-accounts-used",
        description = "Re-sync cape and skin accounts used/owned counts from players collection"
)
@Component
@Slf4j
public class ResyncAccountsUsedCommand implements Runnable {

    private final MongoTemplate mongoTemplate;

    public ResyncAccountsUsedCommand(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run() {
        log.info("Starting resync of skin accountsUsed and cape accountsOwned");

        resyncSkinAccountsUsed();
        resyncCapeAccountsOwned();

        log.info("Resync completed");
    }

    private void resyncSkinAccountsUsed() {
        Aggregation skinAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("skin").exists(true).ne(null)),
                Aggregation.group("skin").count().as("count")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(
                skinAgg,
                PlayerDocument.class,
                Document.class
        );
        List<Document> docs = results.getMappedResults();

        mongoTemplate.updateMulti(
                new Query(),
                new Update().set("accountsUsed", 0L),
                SkinDocument.class
        );

        int updated = 0;
        for (Document doc : docs) {
            UUID skinId = doc.get("_id", UUID.class);
            Number countNum = doc.get("count", Number.class);
            if (skinId == null || countNum == null) continue;
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(skinId)),
                    new Update().set("accountsUsed", countNum.longValue()),
                    SkinDocument.class
            );
            updated++;
        }
        log.info("Resynced accountsUsed for {} skins", updated);
    }

    private void resyncCapeAccountsOwned() {
        Aggregation capeAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("cape").exists(true).ne(null)),
                Aggregation.group("cape").count().as("count")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(
                capeAgg,
                PlayerDocument.class,
                Document.class
        );
        List<Document> docs = results.getMappedResults();

        mongoTemplate.updateMulti(
                new Query(),
                new Update().set("accountsOwned", 0L),
                CapeDocument.class
        );

        int updated = 0;
        for (Document doc : docs) {
            UUID capeId = doc.get("_id", UUID.class);
            Number countNum = doc.get("count", Number.class);
            if (capeId == null || countNum == null) continue;
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(capeId)),
                    new Update().set("accountsOwned", countNum.longValue()),
                    CapeDocument.class
            );
            updated++;
        }
        log.info("Resynced accountsOwned for {} capes", updated);
    }
}
