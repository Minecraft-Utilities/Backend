package xyz.mcutils.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "xyz.mcutils.backend.repository.mongo")
public class MongoConfig {
    @Autowired
    private MongoMappingContext mongoMappingContext;

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Autowired
    void setMapKeyDotReplacement(MappingMongoConverter mappingMongoConverter) {
        mappingMongoConverter.setMapKeyDotReplacement("-DOT");
    }

    @PostConstruct
    public void initIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
        
        // Create indexes for all persistent entities
        mongoMappingContext.getPersistentEntities().forEach(entity -> {
            IndexOperations indexOps = mongoTemplate.indexOps(entity.getCollection());
            resolver.resolveIndexFor(entity.getTypeInformation()).forEach(indexOps::createIndex);
        });
    }
}
