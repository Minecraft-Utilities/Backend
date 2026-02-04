package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.DNSUtils;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.impl.server.ServerPreviewRenderer;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.cache.CachedMinecraftServer;
import xyz.mcutils.backend.model.cache.CachedServerPreview;
import xyz.mcutils.backend.model.dns.DNSRecord;
import xyz.mcutils.backend.model.dns.impl.ARecord;
import xyz.mcutils.backend.model.dns.impl.SRVRecord;
import xyz.mcutils.backend.model.server.Favicon;
import xyz.mcutils.backend.model.server.MinecraftServer;
import xyz.mcutils.backend.model.server.Platform;
import xyz.mcutils.backend.model.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.repository.MinecraftServerCacheRepository;
import xyz.mcutils.backend.repository.ServerPreviewCacheRepository;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ServerService {
    public static final String DEFAULT_SERVER_ICON = "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";

    @Value("${mc-utils.cache.server-previews.enabled}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.server-preview.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.server-preview.limits.min_size}")
    private int minPreviewSize;

    @Value("${mc-utils.renderer.server-preview.limits.max_size}")
    private int maxPreviewSize;

    @Value("${mc-utils.server-pinger.java.timeout}")
    private int javaPingerTimeout;

    @Value("${mc-utils.server-pinger.bedrock.timeout}")
    private int bedrockPingerTimeout;

    private final MojangService mojangService;
    private final MinecraftServerCacheRepository serverCacheRepository;
    private final ServerPreviewCacheRepository serverPreviewCacheRepository;

    @Autowired
    public ServerService(MojangService mojangService, MinecraftServerCacheRepository serverCacheRepository, ServerPreviewCacheRepository serverPreviewCacheRepository) {
        this.mojangService = mojangService;
        this.serverCacheRepository = serverCacheRepository;
        this.serverPreviewCacheRepository = serverPreviewCacheRepository;
    }

    /**
     * Ping a server to get the server information.
     *
     * @param platformName the name of the platform
     * @param hostname the hostname of the server
     * @return the server
     */
    public CachedMinecraftServer getServer(String platformName, String hostname) {
        Platform platform = EnumUtils.getEnumConstant(Platform.class, platformName.toUpperCase());
        if (platform == null) {
            log.debug("Invalid platform: {} for server {}", platformName, hostname);
            throw new BadRequestException("Invalid platform: '%s'".formatted(platformName));
        }
        int port = platform.getDefaultPort();
        if (hostname.contains(":")) {
            String[] parts = hostname.split(":");
            hostname = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.debug("Invalid port: {} for server {}", parts[1], hostname);
                throw new BadRequestException("Invalid port: '%s'".formatted(parts[1]));
            }
        }
        String key = "%s-%s-%s".formatted(platformName, hostname, port);
        log.debug("Getting server: {}:{}", hostname, port);
        
        // Check if the server is cached
        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedMinecraftServer> cached = serverCacheRepository.findById(key);
            if (cached.isPresent()) {
                CachedMinecraftServer server = cached.get();
                log.debug("Got server {}:{} from cache in {}ms", hostname, port, System.currentTimeMillis() - cacheStart);
                server.setCached(true);
                return server;
            }
        }

        List<DNSRecord> dnsRecords = new ArrayList<>();

        SRVRecord srvRecord = platform == Platform.JAVA ? DNSUtils.resolveSRV(hostname) : null; // Resolve the SRV record
        if (srvRecord != null) { // SRV was resolved, use the hostname and port
            dnsRecords.add(srvRecord); // Going to need this for later
            InetSocketAddress socketAddress = srvRecord.getSocketAddress();
            hostname = socketAddress.getHostName();
            port = socketAddress.getPort();
        }

        ARecord aRecord = DNSUtils.resolveA(hostname); // Resolve the A record so we can get the IPv4 address
        String ip = aRecord == null ? null : aRecord.getAddress(); // Get the IP address
        if (ip != null) { // Was the IP resolved?
            dnsRecords.add(aRecord); // Going to need this for later
            log.debug("Resolved hostname: {} -> {}", hostname, ip);
        }

        long pingStart = System.currentTimeMillis();
        CachedMinecraftServer server = new CachedMinecraftServer(
                key,
                platform.getPinger().ping(hostname, ip, port, dnsRecords.toArray(new DNSRecord[0]), platform == Platform.JAVA ? javaPingerTimeout : bedrockPingerTimeout)
        );
        log.debug("Successfully pinged server: {}:{} in {}ms", hostname, port, System.currentTimeMillis() - pingStart);

        // Populate the server's ip lookup data
        server.getServer().lookupIp();

        // Check if the server is blocked by Mojang
        if (platform == Platform.JAVA) {
            ((JavaMinecraftServer) server.getServer()).setMojangBlocked(mojangService.isServerBlocked(hostname));
        }

        if (cacheEnabled) {
            this.serverCacheRepository.save(server);
        }
        return server;
    }

    /**
     * Gets the server favicon.
     *
     * @param hostname the hostname of the server
     * @return the server favicon, null if not found
     */
    public byte[] getServerFavicon(String hostname) {
        String icon = null; // The server base64 icon
        try {
            Favicon favicon = ((JavaMinecraftServer) getServer(Platform.JAVA.name(), hostname).getServer()).getFavicon();
            if (favicon != null) { // Use the server's favicon
                icon = favicon.getBase64();
                icon = icon.substring(icon.indexOf(",") + 1); // Remove the data type from the server icon
            }
        } catch (BadRequestException | NotFoundException ignored) {
            // Safely ignore these, we will use the default server icon
        }
        if (icon == null) { // Use the default server icon
            icon = DEFAULT_SERVER_ICON;
        }
        return Base64.getDecoder().decode(icon); // Return the decoded favicon
    }

    /**
     * Gets the server list preview image.
     *
     * @param cachedServer the server to get the preview of
     * @param platform the platform of the server
     * @param size the size of the preview
     * @return the server preview
     */
    public byte[] getServerPreview(CachedMinecraftServer cachedServer, String platform, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Skin rendering is currently disabled");
        }
        if (size < minPreviewSize || size > maxPreviewSize) {
            throw new BadRequestException("Invalid server preview size. Must be between " + minPreviewSize + " and " + maxPreviewSize);
        }

        MinecraftServer server = cachedServer.getServer();
        log.debug("Getting preview for server: {}:{} (size {})", server.getHostname(), server.getPort(), size);
        String key = "%s-%s-%s-%s".formatted(platform, server.getHostname(), server.getPort(), size);

        // Check if the server preview is cached
        long cacheStart = System.currentTimeMillis();
        Optional<CachedServerPreview> cached = serverPreviewCacheRepository.findById(key);
        if (cached.isPresent() && cacheEnabled) {
            log.debug("Got server preview {}:{} from cache in {}ms", server.getHostname(), server.getPort(), System.currentTimeMillis() - cacheStart);
            return cached.get().getBytes();
        }

        long renderStart = System.currentTimeMillis();
        byte[] preview = ImageUtils.imageToBytes(ServerPreviewRenderer.INSTANCE.render(server, size));
        log.debug("Took {}ms to render preview for server: {}:{}", System.currentTimeMillis() - renderStart, server.getHostname(), server.getPort());

        CachedServerPreview serverPreview = new CachedServerPreview(key, preview);
        
        // don't save to cache in development
        if (cacheEnabled) {
            CompletableFuture.runAsync(() -> serverPreviewCacheRepository.save(serverPreview), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for server preview {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return preview;
    }
}
