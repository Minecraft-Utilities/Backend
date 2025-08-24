package xyz.mcutils.backend.model.websocket;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TrackedAccountsMessage {
    /**
     * The amount of players tracked on McUtils.
     */
    private int playersTracked;
}
