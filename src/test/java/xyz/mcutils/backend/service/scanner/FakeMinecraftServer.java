package xyz.mcutils.backend.service.scanner;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal fake Minecraft Java server for tests: speaks the status protocol (handshake + status
 * request) and answers with a configurable status JSON. Ports marked {@code silent} accept the
 * connection and close without answering, mimicking a non-MC service squatting on a port.
 */
final class FakeMinecraftServer implements AutoCloseable {

    private static final byte STATUS_PACKET_ID = 0x00;

    private final List<ServerSocket> sockets = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();

    /**
     * @param ports     the local ports to bind (127.0.0.1)
     * @param jsonPerPort the status response JSON per port (same size as {@code ports});
     *                    {@code null} marks that port silent (accept then close without answering)
     */
    FakeMinecraftServer(List<Integer> ports, List<String> jsonPerPort) throws IOException {
        for (int i = 0; i < ports.size(); i++) {
            String json = jsonPerPort.get(i);
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", ports.get(i)));
            sockets.add(socket);
            byte[] response = json == null ? null : buildStatusResponse(json);
            threads.add(Thread.ofPlatform().daemon(true).name("fake-mc-" + ports.get(i)).start(() -> acceptLoop(socket, response)));
        }
    }

    static String statusJson(String version, int online, int max, String sampleJson) {
        return "{\"version\":{\"name\":\"" + version + "\",\"protocol\":767},\"players\":{\"online\":" + online
                + ",\"max\":" + max + ",\"sample\":" + sampleJson + "},\"description\":{\"text\":\"fake\"}}";
    }

    static String sampleEntry(String name, String uuid) {
        return "{\"name\":\"" + name + "\",\"id\":\"" + uuid + "\"}";
    }

    @Override
    public void close() {
        for (ServerSocket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        for (Thread thread : threads) {
            try {
                thread.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Finds a base port where all {@code offsets} (absolute ports) are bindable on 127.0.0.1.
     */
    static int findBasePort(int... offsets) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int base;
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress("127.0.0.1", 0));
                base = probe.getLocalPort();
            } catch (IOException e) {
                throw new IllegalStateException("No ephemeral port available", e);
            }
            List<ServerSocket> held = new ArrayList<>();
            boolean allFree = true;
            for (int offset : offsets) {
                try {
                    ServerSocket s = new ServerSocket();
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress("127.0.0.1", base + offset));
                    held.add(s);
                } catch (IOException e) {
                    allFree = false;
                    break;
                }
            }
            for (ServerSocket s : held) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
            if (allFree) {
                return base;
            }
        }
        throw new IllegalStateException("Could not find a free run of ports " + Arrays.toString(offsets));
    }

    private void acceptLoop(ServerSocket socket, byte[] response) {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                client.setSoTimeout(3_000);
                DataInputStream in = new DataInputStream(client.getInputStream());
                readFrame(in); // handshake
                readFrame(in); // status request
                if (response == null) {
                    continue; // silent port: drop the connection without answering
                }
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                out.write(response);
                out.flush();
            } catch (IOException ignored) {
                // client disconnected or timed out
            }
        }
    }

    private static void readFrame(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        long remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0 && in.read() == -1) {
                throw new IOException("Premature end of frame");
            }
            remaining -= skipped > 0 ? skipped : 1;
        }
    }

    private static byte[] buildStatusResponse(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int packetLength = 1 + varIntLength(jsonBytes.length) + jsonBytes.length;
        writeVarInt(data, packetLength);
        data.writeByte(STATUS_PACKET_ID);
        writeVarInt(data, jsonBytes.length);
        data.write(jsonBytes);
        return out.toByteArray();
    }

    private static int varIntLength(int value) {
        int length = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            length++;
        }
        return length;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        }
    }
}