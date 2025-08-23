package xyz.mcutils.backend.service;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import io.micrometer.common.lang.NonNull;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.jodah.expiringmap.ExpirationPolicy;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.ExpiringSet;
import xyz.mcutils.backend.common.Proxies;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.model.token.MojangUsernameToUuidToken;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2(topic = "Mojang Service")
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

    /**
     * The interval to fetch the blocked servers from Mojang.
     */
    private static final long FETCH_BLOCKED_SERVERS_INTERVAL = TimeUnit.HOURS.toMillis(6L);

    /**
     * A list of banned server hashes provided by Mojang.
     * <p>
     * This is periodically fetched from Mojang, see
     * {@link #fetchBlockedServers()} for more info.
     * </p>
     *
     * @see <a href="https://wiki.vg/Mojang_API#Blocked_Servers">Mojang API</a>
     */
    private List<String> bannedServerHashes;

    /**
     * A cache of blocked server hostnames.
     *
     * @see #isServerHostnameBlocked(String) for more
     */
    private final ExpiringSet<String> blockedServersCache = new ExpiringSet<>(ExpirationPolicy.CREATED, 10L, TimeUnit.MINUTES);

    /**
     * The status of the Mojang API.
     */
    private final List<Map<String, Object>> mojangServerStatus = new ArrayList<>();

    public MojangService() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchBlockedServers();
            }
        }, 0L, FETCH_BLOCKED_SERVERS_INTERVAL);
    }

    /**
     * Fetch a list of blocked servers from Mojang.
     */
    @SneakyThrows
    private void fetchBlockedServers() {
        log.info("Fetching blocked servers from Mojang");
        try (
                InputStream inputStream = new URL(FETCH_BLOCKED_SERVERS).openStream();
                Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\n")
        ) {
            List<String> hashes = new ArrayList<>();
            while (scanner.hasNext()) {
                hashes.add(scanner.next());
            }
            bannedServerHashes = Collections.synchronizedList(hashes);
            log.info("Fetched {} banned server hashes", bannedServerHashes.size());
        } catch (IOException e) {
            log.error("Failed to fetch blocked servers from Mojang", e);
        }
    }

    /**
     * Check if the server with the
     * given hostname is blocked by Mojang.
     *
     * @param hostname the server hostname to check
     * @return whether the hostname is blocked
     */
    public boolean isServerBlocked(@NonNull String hostname) {
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
     * Check if the hash for the given
     * hostname is in the blocked server list.
     *
     * @param hostname the hostname to check
     * @return whether the hostname is blocked
     */
    private boolean isServerHostnameBlocked(@NonNull String hostname) {
        // Check the cache first for the hostname
        if (blockedServersCache.contains(hostname)) {
            return true;
        }
        String hashed = Hashing.sha1().hashBytes(hostname.toLowerCase().getBytes(StandardCharsets.ISO_8859_1)).toString();
        boolean blocked = bannedServerHashes.contains(hashed); // Is the hostname blocked?
        if (blocked) { // Cache the blocked hostname
            blockedServersCache.add(hostname);
        }
        return blocked;
    }

    /**
     * Gets the Session Server profile of the
     * player with the given UUID.
     *
     * @param id the uuid or name of the player
     * @return the profile
     */
    public MojangProfileToken getProfile(String id) {
        return WebRequest.getAsEntity(Proxies.getNextProxy() + "/" + SESSION_SERVER_ENDPOINT + "/session/minecraft/profile/" + id, MojangProfileToken.class);
    }

    /**
     * Gets the UUID of the player using
     * the name of the player.
     *
     * @param id the name of the player
     * @return the profile
     */
    public MojangUsernameToUuidToken getUuidFromUsername(String id) {
        return WebRequest.getAsEntity(Proxies.getNextProxy() + "/" + API_ENDPOINT + "/users/profiles/minecraft/" + id, MojangUsernameToUuidToken.class);
    }
}
