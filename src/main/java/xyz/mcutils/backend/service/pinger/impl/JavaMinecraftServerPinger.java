package xyz.mcutils.backend.service.pinger.impl;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.JavaMinecraftVersion;
import xyz.mcutils.backend.common.packet.impl.java.JavaPacketHandshakingInSetProtocol;
import xyz.mcutils.backend.common.packet.impl.java.JavaPacketStatusInStart;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;
import xyz.mcutils.backend.service.pinger.MinecraftServerPinger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

/**
 * @author Braydon
 */
@Slf4j
public final class JavaMinecraftServerPinger implements MinecraftServerPinger<JavaMinecraftServer> {
    /**
     * Ping the server with the given hostname and port.
     *
     * @param hostname the hostname of the server
     * @param port     the port of the server
     * @return the server that was pinged
     */
    @Override
    public JavaMinecraftServer ping(String hostname, String ip, int port, DNSRecord[] records, int timeout) {
        return JavaMinecraftServer.create(hostname, ip, port, records, pingToken(hostname, ip, port, records, timeout));
    }

    /**
     * Performs the Java status protocol handshake and returns the raw status token, skipping the
     * expensive domain-object materialization ({@link JavaMinecraftServer#create} decodes the
     * favicon, colorizes sample names, and serializes MOTD components). The server tracker uses
     * this token path at scan scale; {@link #ping} delegates to it so behavior is unchanged.
     *
     * @param hostname the hostname of the server
     * @param ip       the resolved ip of the server, may be null to resolve the hostname
     * @param port     the port of the server
     * @param timeout  the connect/read timeout in milliseconds
     * @return the parsed status token
     */
    public JavaServerStatusToken pingToken(String hostname, String ip, int port, DNSRecord[] records, int timeout) {
        log.debug("Pinging {}:{}...", hostname, port);

        // Open a socket connection to the server
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            // Connect to the already-resolved IP instead of re-resolving the hostname through the
            // JVM resolver (which bypasses DNSService's cache and can pick a different address
            // family, e.g. IPv6 for an IPv4-only server, causing a full timeout).
            InetAddress target = (ip != null && !ip.isBlank()) ? InetAddress.getByName(ip) : InetAddress.getByName(hostname);
            socket.connect(new InetSocketAddress(target, port), timeout);
            socket.setSoTimeout(timeout);

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

                return Constants.GSON.fromJson(packetStatusInStart.getResponse(), JavaServerStatusToken.class);
            }
        } catch (IOException ex) {
            if (ex instanceof UnknownHostException) {
                throw new BadRequestException("Unknown hostname '%s'".formatted(hostname));
            }
            else if (ex instanceof ConnectException || ex instanceof SocketTimeoutException) {
                throw new BadRequestException("Server '%s' did not respond to ping".formatted(hostname));
            }
            else {
                throw new BadRequestException("An error occurred pinging '%s:%s': %s".formatted(hostname, port, ex.getLocalizedMessage()));
            }
        }
    }
}