package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.skin.Skin;

public interface SkinRepository extends MongoRepository<Skin, String> {}
