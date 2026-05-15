package xyz.mcutils.backend.model.domain.player.history;

import java.time.Instant;
import java.util.UUID;

public record RecentUsernameChange(UUID playerId, String newUsername, String previousUsername, Instant timestamp) {}
