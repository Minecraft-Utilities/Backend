package xyz.mcutils.backend.model.domain.serverregistry;

import xyz.mcutils.backend.model.domain.server.Platform;

import java.util.List;

public record ServerRegistryEntry(
        String serverId,
        String displayName,
        List<String> hostnames,
        List<String> wildcardHostnames,
        String backgroundImageUrl,
        Platform platform
) { }
