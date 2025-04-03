package xyz.mcutils.backend.model.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.UUID;

@AllArgsConstructor
@Getter @RedisHash(value = "playerUpdateQueue")
public class PlayerUpdateQueueItem {
    /**
     * The UUID of the player to update.
     */
    @Id private UUID uuid;

    /**
     * When this player was added to the queue.
     */
    private long timeAdded;
}
