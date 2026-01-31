package xyz.mcutils.backend.service;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Braydon
 */
@Service @Log4j2(topic = "MaxMind")
public class MaxMindService {
    /**
     * The directory to store databases.
     */
    private static final File DATABASES_DIRECTORY = new File("databases");

    /**
     * The endpoint to download database files from.
     */
    private static final String DATABASE_DOWNLOAD_ENDPOINT = "https://download.maxmind.com/app/geoip_download?edition_id=%s&license_key=%s&suffix=tar.gz";

    @Value("${maxmind.license}")
    private String license;

    /**
     * The currently loaded databases.
     */
    private static final Map<Database, DatabaseReader> DATABASES = new HashMap<>();

    @PostConstruct
    public void onInitialize() {
        // Load the databases
        if (!license.equals("CHANGE_ME")) {
            loadDatabases();
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
     * Lookup a city by the given address.
     *
     * @param ip the address
     * @return the city response, null if none
     */
    @SneakyThrows
    public static CityResponse lookupCity(@NonNull String ip) {
        DatabaseReader database = getDatabase(Database.CITY);
        try {
            return database == null ? null : database.city(InetAddress.getByName(ip));
        } catch (AddressNotFoundException ignored) {
            // Safely ignore this and return null instead
            return null;
        }
    }

    /**
     * Lookup an ASN by the given address.
     *
     * @param ip the address
     * @return the asn response, null if none
     */
    @SneakyThrows
    public static AsnResponse lookupAsn(@NonNull String ip) {
        DatabaseReader database = getDatabase(Database.ASN);
        try {
            return database == null ? null : database.asn(InetAddress.getByName(ip));
        } catch (AddressNotFoundException ignored) {
            // Safely ignore this and return null instead
            return null;
        }
    }

    /**
     * Load the databases.
     */
    @SneakyThrows
    private void loadDatabases() {
        loadDatabases(false);
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
     *
     * @param isScheduled whether this is a scheduled update
     */
    @SneakyThrows
    private void loadDatabases(boolean isScheduled) {
        if (isScheduled) {
            log.info("Starting scheduled database check...");
        }
        if (!isScheduled) {
            log.info("Initializing MaxMind databases...");
        }

        // Create the directory if it doesn't exist
        if (!DATABASES_DIRECTORY.exists()) {
            DATABASES_DIRECTORY.mkdirs();
            log.debug("Created databases directory at {}", DATABASES_DIRECTORY.getAbsolutePath());
        }

        int updatedCount = 0;
        int loadedCount = 0;

        // Download missing databases
        for (Database database : Database.values()) {
            File databaseFile = new File(DATABASES_DIRECTORY, database.getEdition() + ".mmdb");
            boolean needsUpdate = false;

            // Check if database exists and needs update
            if (databaseFile.exists()) {
                long ageInMillis = System.currentTimeMillis() - databaseFile.lastModified();
                long daysOld = ageInMillis / (24L * 60L * 60L * 1000L);

                if (ageInMillis > 3L * 24L * 60L * 60L * 1000L) {
                    needsUpdate = true;
                    log.info("Database {} is {} days old (max 3 days), updating...", database.getEdition(), daysOld);

                    // Close the existing database reader before deleting
                    DatabaseReader existingReader = DATABASES.get(database);
                    if (existingReader != null) {
                        existingReader.close();
                        DATABASES.remove(database);
                        log.debug("Closed existing database reader for {}", database.getEdition());
                    }

                    FileUtils.deleteQuietly(databaseFile);
                    updatedCount++;
                }

                if (!needsUpdate && isScheduled) {
                    log.debug("Database {} is {} days old, no update needed", database.getEdition(), daysOld);
                }
            }

            // Handle first-time download
            if (!databaseFile.exists() && !needsUpdate) {
                log.info("Database {} not found, downloading for the first time...", database.getEdition());
                loadedCount++;
            }

            // Download if needed
            if (!databaseFile.exists()) {
                downloadDatabase(database, databaseFile);
            }

            // Load the database if not already loaded
            if (DATABASES.containsKey(database)) {
                continue;
            }

            DATABASES.put(database, new DatabaseReader.Builder(databaseFile)
                    .withCache(new CHMCache()) // Enable caching
                    .build()
            );
            log.info("Successfully loaded database: {}", database.getEdition());
        }

        // Log completion summary
        if (isScheduled && updatedCount > 0) {
            log.info("Scheduled check complete: {} database(s) updated", updatedCount);
            return;
        }

        if (!isScheduled) {
            log.info("Initialization complete: {} database(s) active ({} new, {} updated)", DATABASES.size(), loadedCount, updatedCount);
        }
    }

    /**
     * Download the required files
     * for the given database.
     *
     * @param database the database to download
     * @param databaseFile the file for the database
     */
    @SneakyThrows
    private void downloadDatabase(@NonNull Database database, @NonNull File databaseFile) {
        File downloadedFile = new File(DATABASES_DIRECTORY, database.getEdition() + ".tar.gz"); // The downloaded file

        // Download the database if required
        if (!downloadedFile.exists()) {
            log.info("Downloading database {}...", database.getEdition());
            long before = System.currentTimeMillis();
            try (
                    BufferedInputStream inputStream = new BufferedInputStream(new URL(DATABASE_DOWNLOAD_ENDPOINT.formatted(database.getEdition(), license)).openStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile)
            ) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
            log.info("Downloaded database {} in {}ms", database.getEdition(), System.currentTimeMillis() - before);
        }

        // Extract the database once downloaded
        log.info("Extracting database {}...", database.getEdition());
        TarGZipUnArchiver archiver = new TarGZipUnArchiver();
        archiver.setSourceFile(downloadedFile);
        archiver.setDestDirectory(DATABASES_DIRECTORY);
        archiver.extract();
        log.info("Extracted database {}", database.getEdition());

        // Locate the database file in the extracted directory
        File[] files = DATABASES_DIRECTORY.listFiles();
        assert files != null; // Ensure files is present
        dirLoop: for (File directory : files) {
            if (!directory.isDirectory() || !directory.getName().startsWith(database.getEdition())) {
                continue;
            }
            File[] downloadedFiles = directory.listFiles();
            assert downloadedFiles != null; // Ensures downloaded files is present

            // Find the file for the database, move it to the
            // correct directory, and delete the downloaded contents
            for (File file : downloadedFiles) {
                if (file.isFile() && file.getName().equals(databaseFile.getName())) {
                    Files.move(file.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Delete the downloaded contents
                    FileUtils.deleteDirectory(directory);
                    FileUtils.deleteQuietly(downloadedFile);

                    break dirLoop; // We're done here
                }
            }
        }
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