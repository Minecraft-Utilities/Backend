package xyz.mcutils.backend.model.dto.request;

public record PlayerViewRequest(String playerQuery, String authToken, String remoteIp, String turnstileToken) {}
