package xyz.mcutils.backend.service;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.skin.SkinManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Thread-safe collector for deferred history writes during batch player refresh.
 * Collects username/skin/cape changes then flushes them in bulk.
 * 
 */
public final class BatchHistoryWriter {

    private static final int BULK_FIND_BATCH = 500;

    private final ConcurrentLinkedQueue<UsernameChange> usernameChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SkinChange> skinChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CapeChange> capeChanges = new ConcurrentLinkedQueue<>();

    public void addUsernameChange(UUID playerId, String newName, Date timestamp) {
        usernameChanges.add(new UsernameChange(playerId, newName, timestamp));
    }

    public void addSkinChange(UUID playerId, UUID skinId, Date timestamp) {
        skinChanges.add(new SkinChange(playerId, skinId, timestamp));
    }

    public void addCapeChange(UUID playerId, UUID capeId, Date timestamp) {
        capeChanges.add(new CapeChange(playerId, capeId, timestamp));
    }

    public void flush(MongoTemplate mongoTemplate, SkinManager skinManager, CapeManager capeManager) {
        flushUsernameHistory(mongoTemplate);
        flushSkinHistory(mongoTemplate, skinManager);
        flushCapeHistory(mongoTemplate, capeManager);
    }

    private void flushUsernameHistory(MongoTemplate mongoTemplate) {
        List<UsernameChange> list = new ArrayList<>(usernameChanges);
        if (list.isEmpty()) {
            return;
        }
        List<UsernameHistoryDocument> docs = list.stream()
                .map(u -> UsernameHistoryDocument.builder()
                        .id(UUID.randomUUID())
                        .playerId(u.playerId())
                        .username(u.newName())
                        .timestamp(u.timestamp())
                        .build())
                .toList();
        MongoUtils.bulkInsertUnordered(mongoTemplate, docs, UsernameHistoryDocument.class);
    }

    private void flushSkinHistory(MongoTemplate mongoTemplate, SkinManager skinManager) {
        List<SkinChange> list = new ArrayList<>(skinChanges);
        if (list.isEmpty()) {
            return;
        }
        List<Tuple<UUID, UUID>> pairs = list.stream().map(s -> new Tuple<>(s.playerId(), s.skinId())).toList();
        Set<Tuple<UUID, UUID>> existing = findExistingPairs(mongoTemplate, SkinHistoryDocument.class, "skin", pairs);
        partitionAndFlushRefHistory(
                list,
                existing,
                SkinChange::playerId,
                SkinChange::skinId,
                SkinChange::timestamp,
                "skin",
                s -> SkinHistoryDocument.builder()
                        .id(UUID.randomUUID())
                        .playerId(s.playerId())
                        .skin(SkinDocument.builder().id(s.skinId()).build())
                        .lastUsed(s.timestamp())
                        .timestamp(s.timestamp())
                        .build(),
                SkinHistoryDocument.class,
                mongoTemplate,
                refId -> skinManager.incrementAccountsUsed(refId, 1)
        );
    }

    private void flushCapeHistory(MongoTemplate mongoTemplate, CapeManager capeManager) {
        List<CapeChange> list = new ArrayList<>(capeChanges);
        if (list.isEmpty()) {
            return;
        }
        List<Tuple<UUID, UUID>> pairs = list.stream().map(c -> new Tuple<>(c.playerId(), c.capeId())).toList();
        Set<Tuple<UUID, UUID>> existing = findExistingPairs(mongoTemplate, CapeHistoryDocument.class, "cape", pairs);
        partitionAndFlushRefHistory(
                list,
                existing,
                CapeChange::playerId,
                CapeChange::capeId,
                CapeChange::timestamp,
                "cape",
                c -> CapeHistoryDocument.builder()
                        .id(UUID.randomUUID())
                        .playerId(c.playerId())
                        .cape(CapeDocument.builder().id(c.capeId()).build())
                        .lastUsed(c.timestamp())
                        .timestamp(c.timestamp())
                        .build(),
                CapeHistoryDocument.class,
                mongoTemplate,
                refId -> capeManager.incrementAccountsOwned(refId, 1)
        );
    }

    private <C, T> void partitionAndFlushRefHistory(
            List<C> list,
            Set<Tuple<UUID, UUID>> existing,
            Function<C, UUID> playerIdGetter,
            Function<C, UUID> refIdGetter,
            Function<C, Date> timestampGetter,
            String refFieldName,
            Function<C, T> documentBuilder,
            Class<T> entityClass,
            MongoTemplate mongoTemplate,
            Consumer<UUID> incrementRef) {
        List<C> toUpdate = new ArrayList<>();
        List<C> toInsert = new ArrayList<>();
        for (C c : list) {
            Tuple<UUID, UUID> key = new Tuple<>(playerIdGetter.apply(c), refIdGetter.apply(c));
            if (existing.contains(key)) {
                toUpdate.add(c);
            } else {
                toInsert.add(c);
            }
        }
        if (!toUpdate.isEmpty()) {
            BulkOperations bulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, entityClass);
            for (C c : toUpdate) {
                bulk.updateOne(
                        Query.query(Criteria.where("playerId").is(playerIdGetter.apply(c)).and(refFieldName).is(refIdGetter.apply(c))),
                        new Update().set("lastUsed", timestampGetter.apply(c)));
            }
            bulk.execute();
        }
        if (!toInsert.isEmpty()) {
            List<T> docs = toInsert.stream().map(documentBuilder).toList();
            MongoUtils.bulkInsertUnordered(mongoTemplate, docs, entityClass);
            for (C c : toInsert) {
                incrementRef.accept(refIdGetter.apply(c));
            }
        }
    }

    private static Set<Tuple<UUID, UUID>> findExistingPairs(MongoTemplate mongoTemplate, Class<?> entityClass,
                                                           String refFieldName, List<Tuple<UUID, UUID>> pairs) {
        Set<Tuple<UUID, UUID>> result = new HashSet<>();
        String collection = mongoTemplate.getCollectionName(entityClass);
        for (int i = 0; i < pairs.size(); i += BULK_FIND_BATCH) {
            int end = Math.min(i + BULK_FIND_BATCH, pairs.size());
            List<Tuple<UUID, UUID>> batch = pairs.subList(i, end);
            List<Criteria> orCriteria = batch.stream()
                    .map(p -> Criteria.where("playerId").is(p.left()).and(refFieldName).is(p.right()))
                    .toList();
            Query q = new Query().addCriteria(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
            q.fields().include("playerId", refFieldName);
            List<org.bson.Document> found = mongoTemplate.find(q, org.bson.Document.class, collection);
            for (org.bson.Document d : found) {
                UUID pid = d.get("playerId", UUID.class);
                Object ref = d.get(refFieldName);
                UUID refId = null;
                if (ref instanceof UUID u) {
                    refId = u;
                } else if (ref instanceof org.bson.Document refDoc) {
                    refId = refDoc.get("$id", UUID.class);
                }
                if (pid != null && refId != null) {
                    result.add(new Tuple<>(pid, refId));
                }
            }
        }
        return result;
    }

    public record UsernameChange(UUID playerId, String newName, Date timestamp) { }
    public record SkinChange(UUID playerId, UUID skinId, Date timestamp) { }
    public record CapeChange(UUID playerId, UUID capeId, Date timestamp) { }
}
