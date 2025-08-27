package xyz.mcutils.backend.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TrackedAccountsMessage {
    /**
     * The amount of players tracked on McUtils.
     */
    private int playersTracked;
}
