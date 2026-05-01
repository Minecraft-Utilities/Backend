package xyz.mcutils.backend.service.pinger;

import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;

/**
 * @param <T> the type of server to ping
 * @author Braydon
 */
public interface MinecraftServerPinger<T extends MinecraftServer> {
    T ping(String hostname, String ip, int port, DNSRecord[] records, int timeout);
}