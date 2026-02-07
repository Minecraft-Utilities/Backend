package xyz.mcutils.backend.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GitHub;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.DomainUtils;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.FuzzySearch;
import xyz.mcutils.backend.model.domain.server.Platform;
import xyz.mcutils.backend.model.domain.serverregistry.ServerRegistryEntry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipInputStream;

@Service @Slf4j
public class ServerRegistryService {
    private static final String REPOSITORY_OWNER = "Minecraft-Utilities";
    private static final String REPOSITORY_NAME = "ServerRegistry";
    private static final String REGISTRY_REPOSITORY = "https://github.com/%s/%s/archive/refs/heads/main.zip".formatted(REPOSITORY_OWNER, REPOSITORY_NAME);
    private static final String MINECRAFT_SERVERS_DIR = "minecraft_servers/";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final int MAX_RETURNED_RESULTS = 5;

    private final GitHub githubClient;
    private String lastSeenHash = null;
    private final List<ServerRegistryEntry> entries = new CopyOnWriteArrayList<>();

    @SneakyThrows
    public ServerRegistryService() {
        githubClient = GitHub.connectAnonymously();
        this.updateRegistry();
    }

    /**
     * Searches the server registry for entries that match the given query.
     *
     * @param query the query to search for
     * @return the entries that match the query, best matches first
     */
    public List<ServerRegistryEntry> searchEntries(String query) {
        return FuzzySearch.search(
                entries,
                query,
                entry -> {
                    List<String> texts = new ArrayList<>();
                    texts.add(entry.displayName());
                    texts.addAll(entry.hostnames());
                    texts.addAll(entry.wildcardHostnames());
                    return texts;
                },
                FuzzySearch.DEFAULT_MAX_FUZZY_DISTANCE,
                MAX_RETURNED_RESULTS
        );
    }

    /**
     * Finds a single registry entry that matches the given hostname, either by exact hostname
     * or by matching one of the entry's wildcard hostname patterns (e.g. {@code *.example.com}).
     *
     * @param hostname the hostname to match (e.g. {@code play.example.com})
     * @return the first matching entry, or empty if none match
     */
    public Optional<ServerRegistryEntry> getEntryByHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return Optional.empty();
        }
        String normalized = hostname.strip().toLowerCase();
        return entries.stream()
                .filter(entry ->
                        entry.hostnames().stream().anyMatch(h -> h != null && h.equalsIgnoreCase(normalized))
                                || entry.wildcardHostnames().stream().anyMatch(p -> DomainUtils.matchesWildcard(p, normalized))
                )
                .findFirst();
    }

    /**
     * Downloads the repository from GitHub.
     *
     * @return the bytes of the zipped repo
     */
    private byte[] downloadZip() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_REPOSITORY))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = Constants.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("Server registry download failed with status code {}.", response.statusCode());
                return null;
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            log.error("Server registry download failed.", e);
            return null;
        }
    }

    /**
     * Extracts all the manifests from the zipped repository and
     * transforms them into {@link ServerRegistryEntry}'s
     *
     * @param zipBytes the zipped repository
     * @return the registry entries
     */
    private List<ServerRegistryEntry> extractManifestsFromZip(byte[] zipBytes) {
        List<ServerRegistryEntry> result = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.contains(MINECRAFT_SERVERS_DIR) || !name.endsWith("/" + MANIFEST_FILE)) {
                    continue;
                }
                String json = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

                JsonObject manifest = Constants.GSON.fromJson(json, JsonObject.class);
                if (manifest == null) {
                    continue;
                }
                String serverId = manifest.get("serverId").getAsString();
                result.add(new ServerRegistryEntry(
                        serverId,
                        manifest.get("displayName").getAsString(),
                        manifest.get("hostnames").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList(),
                        manifest.get("wildcardHostnames").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList(),
                        "https://github.com/%s/%s/blob/main/minecraft_servers/%s/background.webp?raw=true".formatted(
                                REPOSITORY_OWNER,
                                REPOSITORY_NAME,
                                serverId
                        ),
                        EnumUtils.getEnumConstant(Platform.class, manifest.get("platform").getAsString())
                ));
            }
        } catch (IOException ignored) { }
        return result;
    }

    /**
     * Update the Server Registry data.
     */
    @Scheduled(cron = "0 */10 * * * *") @SneakyThrows
    private void updateRegistry() {
        Optional<GHCommit> commit = Arrays.stream(githubClient.getRepository(REPOSITORY_OWNER + "/" + REPOSITORY_NAME).listCommits().toArray()).findFirst();
        GHCommit ghCommit = commit.orElse(null);
        String commitHash = Objects.requireNonNull(ghCommit).getSHA1();

        if (lastSeenHash != null && lastSeenHash.equals(commitHash)) {
            return;
        }

        log.info("Updating Server Registry...");
        byte[] zipBytes = downloadZip();
        if (zipBytes == null) {
            return;
        }
        List<ServerRegistryEntry> newEntries = extractManifestsFromZip(zipBytes);
        entries.clear();
        entries.addAll(newEntries);

        log.info("Found {} server registry entries! (commit hash: {})", entries.size(), commitHash);

        lastSeenHash = commitHash;
    }
}
