package xyz.mcutils.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Collation;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

/**
 * Ensures MongoDB indexes required for efficient queries.
 * The case-insensitive username index is used by {@code findByUsernameStartingWithIgnoreCase}.
 */
@Configuration
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureIndexes() {
        // Case-insensitive index for username prefix search (regex with collation uses this)
        mongoTemplate.indexOps(PlayerDocument.class).createIndex(
                new Index().on("username", Sort.Direction.ASC)
                        .named("username_case_insensitive")
                        .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()))
        );
    }
}
