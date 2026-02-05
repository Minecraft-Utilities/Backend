package xyz.mcutils.backend.model.cache;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@AllArgsConstructor
@Setter @Getter @EqualsAndHashCode
@RedisHash(value = "playerCapePart", timeToLive = -1) // do not expire
public class CachedPlayerCapePart {

    /**
     * The ID of the cape part (e.g. capeId-size).
     */
    @Id @NonNull private String id;

    /**
     * The cape part bytes (PNG).
     */
    private byte[] bytes;
}
