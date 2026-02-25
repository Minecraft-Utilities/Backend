package xyz.mcutils.backend.common;

import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic MongoDB helpers. Use to run queries with field projection without defining
 * projection types—e.g. {@link #findWithFields} returns raw {@link Document}s that you
 * map to DTOs or domain objects.
 */
public class MongoUtils {
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

    /**
     * Inserts the given documents in one unordered bulk operation (MongoDB can parallelize, avoids long-held write locks).
     * No-op if the list is null or empty.
     *
     * @param template     MongoTemplate
     * @param documents    documents to insert
     * @param entityClass  entity class used to resolve collection name
     */
    public static <T> void bulkInsertUnordered(MongoTemplate template, List<T> documents, Class<T> entityClass) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        BulkOperations bulk = template.bulkOps(BulkMode.UNORDERED, entityClass);
        bulk.insert(documents);
        bulk.execute();
    }

    /**
     * Runs unordered bulk updateOne for each (id, count): inc the given field by count for document _id.
     * No-op if the map is null or empty.
     *
     * @param template   MongoTemplate
     * @param entityClass entity class used to resolve collection name
     * @param idToCount  map of document _id to increment value
     * @param fieldName  field to inc (e.g. "accountsUsed", "submittedUuids")
     */
    public static <T> void bulkIncUnordered(MongoTemplate template, Class<T> entityClass,
                                           Map<UUID, Long> idToCount, String fieldName) {
        if (idToCount == null || idToCount.isEmpty()) {
            return;
        }
        BulkOperations bulk = template.bulkOps(BulkMode.UNORDERED, entityClass);
        for (Map.Entry<UUID, Long> e : idToCount.entrySet()) {
            bulk.updateOne(
                    Query.query(Criteria.where("_id").is(e.getKey())),
                    new Update().inc(fieldName, e.getValue()));
        }
        bulk.execute();
    }
}
