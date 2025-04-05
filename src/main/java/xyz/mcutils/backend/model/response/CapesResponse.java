package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.model.player.Cape;

/**
 * The response for the /capes endpoint.
 *
 * @author Fascinated
 */
@AllArgsConstructor
@Getter
public class CapesResponse {
    /**
     * All the tracked capes.
     */
    private Cape[] capes;
}
