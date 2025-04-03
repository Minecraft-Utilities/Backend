package xyz.mcutils.backend.model.player;

import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString
public class UUIDSubmission {
    /**
     * The UUID of the account that has sent the uuids
     */
    private UUID accountUuid;

    /**
     * The UUIDs to submit
     */
    private UUID[] uuids;
}
