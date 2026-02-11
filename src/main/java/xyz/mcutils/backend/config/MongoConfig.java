package xyz.mcutils.backend.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "xyz.mcutils.backend.repository.mongo")
public class MongoConfig {

    @Bean
    public MongoClient mongoClient(
            @Value("${mc-utils.mongo.uri}") String uri,
            @Value("${mc-utils.mongo.max-connection-pool-size:250}") int maxConnectionPoolSize,
            @Value("${mc-utils.mongo.min-connection-pool-size:25}") int minConnectionPoolSize) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyToConnectionPoolSettings(builder -> builder
                        .maxSize(maxConnectionPoolSize)
                        .minSize(minConnectionPoolSize))
                .build();
        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(
            MongoClient mongoClient,
            @Value("${mc-utils.mongo.uri}") String uri) {
        String database = new ConnectionString(uri).getDatabase();
        if (database == null || database.isBlank()) {
            database = "mcutils";
        }
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }
}