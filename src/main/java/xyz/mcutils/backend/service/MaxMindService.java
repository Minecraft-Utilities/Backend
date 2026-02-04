package xyz.mcutils.backend.service;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Postal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.asn.AsnLookup;
import xyz.mcutils.backend.model.geo.GeoLocation;
import xyz.mcutils.backend.model.response.IpLookup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author Braydon
 */
@Service
@Slf4j
public class MaxMindService {
    public static MaxMindService INSTANCE;

    private static final String DATABASE_DOWNLOAD_ENDPOINT = "https://download.maxmind.com/app/geoip_download?edition_id=%s&license_key=%s&suffix=tar.gz";
    private static final Map<Database, DatabaseReader> DATABASES = new HashMap<>();

    @Value("${mc-utils.maxmind.license}")
    private String license;

    @Value("${mc-utils.maxmind.database-dir:databases}")
    private String databaseDirPath;

    @PostConstruct
    public void onInitialize() {
        // Load the databases
        if (!license.isEmpty()) {
            loadDatabases();
        }
        INSTANCE = this;
    }

    /**
     * Lookup the IP address and return the response.
     *
     * @param ip the IP address to lookup
     * @return the IP lookup response
     */
    @Cacheable(value = "geoLookup", key = "#ip")
    public IpLookup lookupIp(@NonNull String ip) {
        log.debug("Getting lookup for IP: {}", ip);

        long lookupStart = System.currentTimeMillis();
        CompletableFuture<GeoLocation> cityFuture = lookupCity(ip);
        CompletableFuture<AsnLookup> asnFuture = lookupAsn(ip);

        CompletableFuture.allOf(cityFuture, asnFuture).join();

        GeoLocation location = cityFuture.join();
        AsnLookup asn = asnFuture.join();

        if (location == null && asn == null) {
            throw new NotFoundException("No data found for IP address: %s".formatted(ip));
        }

        log.debug(
                "Got IP lookup for {} from {}ms",
                ip, System.currentTimeMillis() - lookupStart
        );
        return new IpLookup(
            ip,
            location,
            asn
        );
    }

    /**
     * Lookup an ASN by the given address.
     *
     * @param ip the address
     * @return a future containing the ASN response, or null if none
     */
    private CompletableFuture<AsnLookup> lookupAsn(@NonNull String ip) {
        return CompletableFuture.supplyAsync(() -> {
            DatabaseReader database = getDatabase(Database.ASN);
            if (database == null) {
                return null;
            }

            try {
                AsnResponse asn = database.asn(InetAddress.getByName(ip));
                if (asn == null) {
                    return null;
                }

                return new AsnLookup(
                        "AS%s".formatted(asn.autonomousSystemNumber()),
                        asn.autonomousSystemOrganization(),
                        asn.network().toString()
                );
            } catch (AddressNotFoundException ignored) {
                return null;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Main.EXECUTOR);
    }

    /**
     * Lookup a city by the given address.
     *
     * @param ip the address
     * @return a future containing the city response, or null if none
     */
    private CompletableFuture<GeoLocation> lookupCity(@NonNull String ip) {
        return CompletableFuture.supplyAsync(() -> {
            DatabaseReader database = getDatabase(Database.CITY);
            if (database == null) {
                return null;
            }

            try {
                CityResponse city = database.city(InetAddress.getByName(ip));
                Country country = city.country();
                Location location = city.location();

                if (country == null || location == null || country.isoCode() == null) {
                    return null;
                }

                Postal postal = city.postal();
                String isoCode = country.isoCode();

                return new GeoLocation(
                        country.name(),
                        isoCode,
                        city.mostSpecificSubdivision().name(),
                        city.city().name(),
                        location.timeZone(),
                        postal != null ? postal.code() : null,
                        location.latitude(),
                        location.longitude(),
                        "https://flagcdn.com/w20/" + isoCode.toLowerCase() + ".webp"
                );
            } catch (AddressNotFoundException ignored) {
                return null;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Main.EXECUTOR);
    }



    /**
     * Get the reader for the given database.
     *
     * @param database the database to get
     * @return the database reader, null if none
     */
    public static DatabaseReader getDatabase(@NonNull Database database) {
        return DATABASES.get(database);
    }

    /**
     * Load the databases.
     */
    @SneakyThrows
    private void loadDatabases() {
        loadDatabases(false);
    }

    /**
     * Load the databases.
     *
     * @param isScheduled whether this is a scheduled update
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SneakyThrows
    private void loadDatabases(boolean isScheduled) {
        File databasesDir = new File(databaseDirPath);
        if (isScheduled) {
            log.info("Starting scheduled database check...");
        }
        if (!isScheduled) {
            log.info("Initializing MaxMind databases...");
        }
        log.debug("Database directory: {}", databasesDir.getAbsolutePath());

        // Create the directory if it doesn't exist
        if (!databasesDir.exists()) {
            databasesDir.mkdirs();
            log.debug("Created databases directory at {}", databasesDir.getAbsolutePath());
        }

        int updatedCount = 0;
        int loadedCount = 0;

        for (Database database : Database.values()) {
            File databaseFile = new File(databasesDir, database.getEdition() + ".mmdb");
            boolean fileExisted = databaseFile.exists();
            boolean needsUpdate = false;

            if (fileExisted) {
                // Only check staleness during scheduled updates; never on startup
                if (isScheduled) {
                    long ageInMillis = System.currentTimeMillis() - databaseFile.lastModified();
                    long daysOld = ageInMillis / (24L * 60L * 60L * 1000L);
                    if (ageInMillis > 3L * 24L * 60L * 60L * 1000L) {
                        needsUpdate = true;
                        log.info("Database {} is {} days old (max 3 days), attempting update...", database.getEdition(), daysOld);
                    } else {
                        log.debug("Database {} is {} days old, no update needed", database.getEdition(), daysOld);
                    }
                }
            } else {
                log.info("Database {} not found, downloading for the first time...", database.getEdition());
                loadedCount++;
            }

            // Close existing reader before update so we can overwrite the file
            if (needsUpdate) {
                DatabaseReader existing = DATABASES.get(database);
                if (existing != null) {
                    existing.close();
                    DATABASES.remove(database);
                }
            }

            // Download only when missing, or when we need an update (download to temp, then replace)
            if (!databaseFile.exists() || needsUpdate) {
                boolean downloaded = downloadDatabase(database, databaseFile, databasesDir);
                if (!downloaded) {
                    if (fileExisted && needsUpdate) {
                        log.warn("Download failed for {} (e.g. rate limit 429); keeping existing database", database.getEdition());
                    } else {
                        log.warn("Download failed for {}; GeoIP for this database will be unavailable. " +
                                "Download manually from MaxMind or retry later.", database.getEdition());
                        continue;
                    }
                } else if (needsUpdate) {
                    updatedCount++;
                }
            }

            // If we deleted for update, we already replaced; otherwise ensure we have a file
            if (!databaseFile.exists()) {
                continue;
            }

            // Close existing reader before loading new file (e.g. after update)
            DatabaseReader existingReader = DATABASES.get(database);
            if (existingReader != null) {
                existingReader.close();
                DATABASES.remove(database);
            }

            DATABASES.put(database, new DatabaseReader.Builder(databaseFile)
                    .withCache(new CHMCache())
                    .build()
            );
            log.info("Successfully loaded database: {}", database.getEdition());
        }

        if (isScheduled && updatedCount > 0) {
            log.info("Scheduled check complete: {} database(s) updated", updatedCount);
        } else if (!isScheduled) {
            log.info("Initialization complete: {} database(s) active ({} new, {} updated)", DATABASES.size(), loadedCount, updatedCount);
        }
    }

    /**
     * Download and extract the database. Does not delete the existing file until the new one is ready.
     *
     * @param database     the database to download
     * @param databaseFile the target .mmdb file
     * @param databasesDir the directory containing databases
     * @return true if download and extraction succeeded, false on failure (e.g. 429 rate limit)
     */
    private boolean downloadDatabase(@NonNull Database database, @NonNull File databaseFile,
                                    @NonNull File databasesDir) {
        File downloadedFile = new File(databasesDir, database.getEdition() + ".tar.gz");

        if (!downloadedFile.exists()) {
            log.info("Downloading database {}...", database.getEdition());
            try {
                URL url = URI.create(DATABASE_DOWNLOAD_ENDPOINT.formatted(database.getEdition(), license)).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    log.warn("MaxMind returned HTTP {} for {} ({}); skipping download",
                            code, database.getEdition(), code == 429 ? "rate limited" : "check MaxMind status");
                    return false;
                }
                long before = System.currentTimeMillis();
                try (
                        BufferedInputStream inputStream = new BufferedInputStream(conn.getInputStream());
                        FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile)
                ) {
                    byte[] dataBuffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(dataBuffer)) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                    }
                }
                log.info("Downloaded database {} in {}ms", database.getEdition(), System.currentTimeMillis() - before);
            } catch (IOException e) {
                log.warn("Failed to download {}: {}", database.getEdition(), e.getMessage());
                FileUtils.deleteQuietly(downloadedFile);
                return false;
            }
        }

        try {
            log.info("Extracting database {}...", database.getEdition());
            TarGZipUnArchiver archiver = new TarGZipUnArchiver();
            archiver.setSourceFile(downloadedFile);
            archiver.setDestDirectory(databasesDir);
            archiver.extract();

            File[] files = databasesDir.listFiles();
            if (files == null) return false;
            for (File directory : files) {
                if (!directory.isDirectory() || !directory.getName().startsWith(database.getEdition())) {
                    continue;
                }
                File[] downloadedFiles = directory.listFiles();
                if (downloadedFiles == null) continue;

                for (File file : downloadedFiles) {
                    if (file.isFile() && file.getName().equals(databaseFile.getName())) {
                        Files.move(file.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        FileUtils.deleteDirectory(directory);
                        FileUtils.deleteQuietly(downloadedFile);
                        log.info("Extracted database {}", database.getEdition());
                        return true;
                    }
                }
            }
            log.warn("Could not find {} in extracted archive", databaseFile.getName());
            return false;
        } catch (Exception e) {
            log.warn("Failed to extract {}: {}", database.getEdition(), e.getMessage());
            FileUtils.deleteQuietly(downloadedFile);
            return false;
        }
    }

    /**
     * Cleanup when the app is destroyed.
     */
    @PreDestroy @SneakyThrows
    public void cleanup() {
        for (DatabaseReader database : DATABASES.values()) {
            database.close();
        }
        DATABASES.clear();
    }

    /**
     * Scheduled task to check and update databases every 3 days.
     * Runs at 2 AM daily to check if databases need updating.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SneakyThrows
    public void scheduledDatabaseUpdate() {
        loadDatabases(true);
    }

    /**
     * A database for MaxMind.
     */
    @AllArgsConstructor @Getter @ToString
    public enum Database {
        CITY("GeoLite2-City"),
        ASN("GeoLite2-ASN");

        /**
         * The edition of this database.
         */
        @NonNull private final String edition;
    }
}