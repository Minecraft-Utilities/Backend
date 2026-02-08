package xyz.mcutils.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoConverterConfig {

    @Autowired
    void configureMappingMongoConverter(MappingMongoConverter mappingMongoConverter) {
        mappingMongoConverter.setMapKeyDotReplacement("-DOT");
        MongoMappingContext mappingContext = (MongoMappingContext) mappingMongoConverter.getMappingContext();
        mappingContext.setAutoIndexCreation(true);
    }
}
