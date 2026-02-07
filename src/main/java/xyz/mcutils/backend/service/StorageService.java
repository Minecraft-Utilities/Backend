package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StorageService {
    public static StorageService INSTANCE;

    private final MinioClient minioClient;
    private Cache<ObjectCacheKey, byte[]> objectCache;

    @SneakyThrows
    @Autowired
    public StorageService(@Value("${mc-utils.s3.endpoint}") String endpoint,
                          @Value("${mc-utils.s3.accessKey}") String accessKey,
                          @Value("${mc-utils.s3.secretKey}") String secretKey,
                          @Value("${mc-utils.cache.s3.enabled}") boolean cacheEnabled,
                          @Value("${mc-utils.cache.s3.ttl}") int objectCacheTtl
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        if (cacheEnabled) {
            this.objectCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(objectCacheTtl, TimeUnit.MINUTES)
                    .build();
        }

        for (Bucket bucket : Bucket.values()) {
            if (this.minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket.getName())
                    .build())) {
                continue;
            }

            this.minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket.getName())
                    .build());
        }
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Uploads a file to the given bucket.
     *
     * @param bucket the bucket to upload to
     * @param fileName the name of the file
     * @param data the data to upload
     */
    @SneakyThrows
    public void upload(Bucket bucket, String fileName, String contentType, byte[] data) {
        try {
            long before = System.currentTimeMillis();
            this.minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket.getName())
                    .object(fileName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)

                    .build());
            if (this.objectCache != null) {
                this.objectCache.put(new ObjectCacheKey(bucket, fileName), data);
            }
            log.debug("Uploaded object {} to bucket {} in {}ms", fileName, bucket.getName(),  System.currentTimeMillis() - before);
        } catch (Exception ex) {
            log.error("Failed to upload file to bucket {}", bucket.getName(), ex);
        }
    }

    /**
     * Gets a file from the given bucket.
     *
     * @param bucket the bucket to get from
     * @param fileName the name of the file
     * @return the file data
     */
    @SneakyThrows
    public byte[] get(Bucket bucket, String fileName) {
        try {
            byte[] object = this.objectCache != null ? this.objectCache.getIfPresent(new ObjectCacheKey(bucket, fileName)) : null;
            if (object != null) {
                return object;
            }

            long before = System.currentTimeMillis();
            byte[] bytes = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(bucket.getName())
                            .object(fileName)
                            .build())
                    .readAllBytes();
            if (this.objectCache != null) {
                this.objectCache.put(new ObjectCacheKey(bucket, fileName), bytes);
            }
            log.debug("Got object {} from bucket {} in {}ms", fileName, bucket.getName(), System.currentTimeMillis() - before);
            return bytes;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Checks if a file exists in the given bucket.
     *
     * @param bucket the bucket to check in
     * @param fileName the name of the file
     * @return true if the file exists, false otherwise
     */
    @SneakyThrows
    public boolean exists(Bucket bucket, String fileName) {
        try {
            return this.minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket.getName())
                .object(fileName)
                .build())
                .object() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Cache key for the in-memory object cache.
     *
     * @param bucket the bucket it is stored in
     * @param fileName the name of the file
     */
    public record ObjectCacheKey(Bucket bucket, String fileName) {}

    @AllArgsConstructor
    @Getter
    public enum Bucket {
        SKINS("mcutils-skins"),
        VANILLA_CAPES("mcutils-vanilla-capes"),
        OPTIFINE_CAPES("mcutils-optifine-capes");

        private final String name;
    }
}
