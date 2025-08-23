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
     * The amount of new UUIDs tracked.
     */
    private int added;

    /**
     * The tota; amount of UUIDs tracked by the player.
     */
    private int totalTracked;
}
