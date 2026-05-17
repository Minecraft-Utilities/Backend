package xyz.mcutils.backend.service;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.metric.impl.api.ExternalApiRequestsMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@Getter
public class MojangService {
    /**
     * The splitter and joiner for dots.
     */
    private static final Splitter DOT_SPLITTER = Splitter.on('.');
    private static final Joiner DOT_JOINER = Joiner.on('.');

    /**
     * The Mojang API endpoints.
     */
    private static final String SESSION_SERVER_ENDPOINT = "https://sessionserver.mojang.com";
    private static final String API_ENDPOINT = "https://api.mojang.com";
    private static final String FETCH_BLOCKED_SERVERS = SESSION_SERVER_ENDPOINT + "/blockedservers";
    private static final String API_MOJANG = "mojang";

    /**
     * A list of banned server hashes provided by Mojang.
     * <p>
     * This is periodically fetched from Mojang, see
     * {@link #updateBlockedServers()} for more info.
     * </p>
     */
    private final Set<String> blockedServerHashes = ConcurrentHashMap.newKeySet();

    private final WebRequest webRequest;

    public MojangService(WebRequest webRequest) {
        this.webRequest = webRequest;
        updateBlockedServers();
    }

    /**
     * Gets the Session Server profile of the
     * player with the given UUID.
     *
     * @param id the uuid or name of the player
     * @return the profile
     */
    public MojangProfileToken getProfile(String id) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            MojangProfileToken result = webRequest.request(SESSION_SERVER_ENDPOINT + "/session/minecraft/profile/" + id).useProxy().as(MojangProfileToken.class);
            success = result != null;
            return result;
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_MOJANG, "player_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Gets the UUID of the player using
     * the name of the player.
     *
     * @param id the name of the player
     * @return the profile
     */
    public MojangUsernameToUuidToken getUuidFromUsername(String id) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            MojangUsernameToUuidToken result = webRequest.request(API_ENDPOINT + "/users/profiles/minecraft/" + id).useProxy().as(MojangUsernameToUuidToken.class);
            success = result != null;
            return result;
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_MOJANG, "username_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Check if the hash for the given
     * hostname is in the blocked server list.
     *
     * @param hostname the hostname to check
     * @return whether the hostname is blocked
     */
    @SuppressWarnings("deprecation")
    private boolean isServerHostnameBlocked(@NonNull String hostname) {
        return blockedServerHashes.contains(Hashing.sha1().hashBytes(hostname.toLowerCase().getBytes(StandardCharsets.ISO_8859_1)).toString());
    }

    /**
     * Check if the server with the
     * given hostname is blocked by Mojang.
     *
     * @param hostname the server hostname to check
     * @return whether the hostname is blocked
     */
    public boolean isServerBlocked(@NonNull String hostname) {
        if (hostname.isEmpty()) {
            return false;
        }

        // Remove trailing dots
        while (hostname.charAt(hostname.length() - 1) == '.') {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        // Is the hostname banned?
        if (isServerHostnameBlocked(hostname)) {
            return true;
        }
        List<String> splitDots = Lists.newArrayList(DOT_SPLITTER.split(hostname)); // Split the hostname by dots
        boolean isIp = splitDots.size() == 4; // Is it an IP address?
        if (isIp) {
            for (String element : splitDots) {
                try {
                    int part = Integer.parseInt(element);
                    if (part >= 0 && part <= 255) { // Ensure the part is within the valid range
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                    // Safely ignore, not a number
                }
                isIp = false;
                break;
            }
        }
        // Check if the hostname is blocked
        if (!isIp && isServerHostnameBlocked("*." + hostname)) {
            return true;
        }
        // Additional checks for the hostname
        while (splitDots.size() > 1) {
            splitDots.remove(isIp ? splitDots.size() - 1 : 0);
            String starredPart = isIp ? DOT_JOINER.join(splitDots) + ".*" : "*." + DOT_JOINER.join(splitDots);
            if (isServerHostnameBlocked(starredPart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fetches the current list of banned server hashes from Mojang and updates {@link #blockedServerHashes}.
     * Runs daily at midnight. Uses the shared HTTP client (connection pooling, proxy if configured).
     */
    @Scheduled(cron = "0 0 0 * * *")
    private void updateBlockedServers() {
        log.info("Fetching blocked servers from Mojang");
        byte[] bytes = webRequest.request(FETCH_BLOCKED_SERVERS).asBytes();
        if (bytes == null) {
            log.error("Failed to fetch blocked servers from Mojang");
            return;
        }
        List<String> hashes = Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\n"));
        blockedServerHashes.clear();
        blockedServerHashes.addAll(hashes);
        log.info("Fetched {} blocked server hashes", blockedServerHashes.size());
    }
}
