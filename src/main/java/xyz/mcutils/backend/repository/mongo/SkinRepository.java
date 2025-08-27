package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.List;
import java.util.Map;

public interface SkinRepository extends MongoRepository<Skin, String> {
    
    /**
     * Finds the most popular skins based on current usage count.
     * 
     * @param pageable pagination parameters
     * @return list of skin IDs ordered by current popularity (most popular first)
     */
    @Aggregation(pipeline = {
        "{ $match: { _id: { $exists: true, $ne: null, $ne: '' } } }",
        "{ $lookup: { from: 'players', localField: '_id', foreignField: 'currentSkinId', as: 'currentUsers' } }",
        "{ $addFields: { currentUsageCount: { $size: '$currentUsers' } } }",
        "{ $match: { currentUsageCount: { $gt: 0 } } }",
        "{ $sort: { currentUsageCount: -1 } }",
        "{ $project: { skinId: '$_id', currentUsageCount: 1 } }"
    })
    List<Map<String, Object>> findMostPopularSkinsRaw(Pageable pageable);
}
