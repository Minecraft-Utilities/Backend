package xyz.mcutils.backend.model.serverregistry;

import xyz.mcutils.backend.model.server.Platform;

import java.util.List;

public record ServerRegistryEntry(
        String serverId,
        String displayName,
        List<String> hostnames,
        List<String> wildcardHostnames,
        String backgroundImageUrl,
        Platform platform
) { }

