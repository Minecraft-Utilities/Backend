package xyz.mcutils.backend.service.pinger.impl;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.packet.impl.bedrock.BedrockPacketUnconnectedPing;
import xyz.mcutils.backend.common.packet.impl.bedrock.BedrockPacketUnconnectedPong;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.server.bedrock.BedrockMinecraftServer;
import xyz.mcutils.backend.service.pinger.MinecraftServerPinger;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * The {@link MinecraftServerPinger} for pinging
 * {@link BedrockMinecraftServer} over UDP.
 *
 * @author Braydon
 */
@Slf4j
public final class BedrockMinecraftServerPinger implements MinecraftServerPinger<BedrockMinecraftServer> {
    /**
     * Ping the server with the given hostname and port.
     *
     * @param hostname the hostname of the server
     * @param port     the port of the server
     * @return the server that was pinged
     */
    @Override
    public BedrockMinecraftServer ping(String hostname, String ip, int port, DNSRecord[] records, int timeout) {
        log.debug("Pinging {}:{}...", hostname, port);
        long before = System.currentTimeMillis(); // Timestamp before pinging

        // Open a socket connection to the server
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(hostname, port));

            long ping = System.currentTimeMillis() - before; // Calculate the ping
            log.debug("Pinged {}:{} in {}ms", hostname, port, ping);

            // Send the unconnected ping packet
            new BedrockPacketUnconnectedPing().process(socket);

            // Handle the received unconnected pong packet
            BedrockPacketUnconnectedPong unconnectedPong = new BedrockPacketUnconnectedPong();
            unconnectedPong.process(socket);
            String response = unconnectedPong.getResponse();
            if (response == null) { // No pong response
                throw new BadRequestException("Server '%s' did not respond to ping".formatted(hostname));
            }
            return BedrockMinecraftServer.create(hostname, ip, port, records, response); // Return the server
        } catch (IOException ex) {
            if (ex instanceof UnknownHostException) {
                throw new BadRequestException("Unknown hostname '%s'".formatted(hostname));
            } else if (ex instanceof SocketTimeoutException) {
                throw new BadRequestException("Server '%s' did not respond to ping".formatted(hostname));
            } else {
                log.debug("An error occurred pinging {}:{}:", hostname, port, ex);
                throw new BadRequestException("An error occurred pinging '%s:%s'".formatted(hostname, port));
            }
        }
    }
}