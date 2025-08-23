package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.player.Cape;

public interface CapeRepository extends MongoRepository<Cape, String> {}
