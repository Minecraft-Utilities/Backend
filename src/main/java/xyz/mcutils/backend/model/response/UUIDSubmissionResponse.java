package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class UUIDSubmissionResponse {
    /**
     * The amount of new UUIDs submitted to the queue.
     */
    private int addedToQueue;

    /**
     * The total amount of UUIDs tracked by the player.
     */
    private int playerSubmissionCount;

    /**
     * The amount of players tracked on McUtils.
     */
    private int playersTracked;
}
