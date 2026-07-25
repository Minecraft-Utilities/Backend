package xyz.mcutils.backend.discovery;

/**
 * A generated username candidate tagged with the strategy that produced it.
 */
public record UsernameCandidate(String username, UsernameDiscoveryStrategy strategy) {
    public String queuePayload() {
        return strategy.name() + ':' + username;
    }

    public static UsernameCandidate parseQueuePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return new UsernameCandidate(payload.strip().toLowerCase(), null);
        }
        String strategyName = payload.substring(0, separator);
        String username = payload.substring(separator + 1).strip().toLowerCase();
        UsernameDiscoveryStrategy strategy;
        try {
            strategy = UsernameDiscoveryStrategy.valueOf(strategyName);
        } catch (IllegalArgumentException e) {
            strategy = null;
        }
        return new UsernameCandidate(username, strategy);
    }
}
