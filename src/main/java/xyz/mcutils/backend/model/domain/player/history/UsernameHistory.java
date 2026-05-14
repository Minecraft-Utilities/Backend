package xyz.mcutils.backend.model.domain.player.history;

import java.time.Instant;

public record UsernameHistory(String newUsername, String previousUsername, Instant timestamp) {}