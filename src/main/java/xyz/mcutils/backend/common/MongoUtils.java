package xyz.mcutils.backend.common;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Generic MongoDB helpers. Use to run queries with field projection without defining
 * projection types—e.g. {@link #findWithFields} returns raw {@link Document}s that you
 * map to DTOs or domain objects.
 */
public final class MongoUtils {

    private MongoUtils() {
    }

    /**
     * Runs a find with only the given fields included (projection). Returns raw documents
     * so callers can map to any type without projection interfaces.
     *
     * @param template    MongoTemplate
     * @param query       query (will be mutated to add field restriction)
     * @param entityClass entity class used only to resolve collection name
     * @param fields      field names to include (e.g. "_id", "username")
     * @return list of documents with only those fields
     */
    public static List<Document> findWithFields(MongoTemplate template, Query query,
                                                Class<?> entityClass, String... fields) {
        var fieldSpec = query.fields();
        for (String field : fields) {
            fieldSpec.include(field);
        }
        String collection = template.getCollectionName(entityClass);
        return template.find(query, Document.class, collection);
    }
}
