package xyz.mcutils.backend.repository.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import xyz.mcutils.backend.model.skin.Skin;

public interface SkinRepository extends MongoRepository<Skin, String> {
    
    /**
     * Gets skins by their IDs - much more efficient than aggregation
     * 
     * @param skinIds list of skin IDs to fetch
     * @return list of skins
     */
    List<Skin> findByIdIn(List<String> skinIds);
}
