package xyz.mcutils.backend.service.pinger.impl;

import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.JavaMinecraftVersion;
import xyz.mcutils.backend.common.packet.impl.java.JavaPacketHandshakingInSetProtocol;
import xyz.mcutils.backend.common.packet.impl.java.JavaPacketStatusInStart;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;
import xyz.mcutils.backend.service.pinger.MinecraftServerPinger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

/**
 * @author Braydon
 */
@Log4j2(topic = "Java Pinger")
public final class JavaMinecraftServerPinger implements MinecraftServerPinger<JavaMinecraftServer> {
    private static final int TIMEOUT = 1500; // The timeout for the socket

    /**
     * Ping the server with the given hostname and port.
     *
     * @param hostname the hostname of the server
     * @param port     the port of the server
     * @return the server that was pinged
     */
    @Override
    public JavaMinecraftServer ping(String hostname, String ip, int port, DNSRecord[] records) {
        log.debug("Pinging {}:{}...", hostname, port);

        // Open a socket connection to the server
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(hostname, port), TIMEOUT);

            // Open data streams to begin packet transaction
            try (DataInputStream inputStream = new DataInputStream(socket.getInputStream()); DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
                // Send the handshake packet
                JavaPacketHandshakingInSetProtocol handshakePacket = new JavaPacketHandshakingInSetProtocol(hostname, port, JavaMinecraftVersion.getLatestVersion().getProtocol());
                handshakePacket.process(inputStream, outputStream);
                outputStream.flush();

                // Send the status request and await the response
                JavaPacketStatusInStart packetStatusInStart = new JavaPacketStatusInStart();
                packetStatusInStart.process(inputStream, outputStream);
                outputStream.flush();

                JavaServerStatusToken token = Constants.GSON.fromJson(packetStatusInStart.getResponse(), JavaServerStatusToken.class);
                return JavaMinecraftServer.create(hostname, ip, port, records, token);
            }
        } catch (IOException ex) {
            if (ex instanceof UnknownHostException) {
                throw new BadRequestException("Unknown hostname '%s'".formatted(hostname));
            } else if (ex instanceof ConnectException || ex instanceof SocketTimeoutException) {
                throw new BadRequestException("Server '%s' did not respond to ping".formatted(hostname));
            } else {
                throw new BadRequestException("An error occurred pinging '%s:%s': %s".formatted(hostname, port, ex.getLocalizedMessage()));
            }
        }
    }
}