package xyz.mcutils.backend.model.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import xyz.mcutils.backend.common.CachedResponse;
import xyz.mcutils.backend.model.player.Player;

import java.io.Serializable;
import java.util.UUID;

/**
 * A cacheable {@link Player}.
 *
 * @author Braydon
 */
@Setter @Getter @EqualsAndHashCode(callSuper = false)
@RedisHash(value = "player", timeToLive = 60L * 60L) // 1 hour (in seconds)
public class CachedPlayer extends CachedResponse implements Serializable {
    /**
     * The unique id of the player.
     */
    @JsonIgnore
    @Id private UUID uniqueId;

    /**
     * The player to cache.
     */
    @JsonUnwrapped
    private Player player;

    public CachedPlayer(UUID uniqueId, Player player) {
        super(true, System.currentTimeMillis());
        this.uniqueId = uniqueId;
        this.player = player;
    }
}