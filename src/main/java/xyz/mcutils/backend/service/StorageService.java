package xyz.mcutils.backend.service;

import io.minio.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@Log4j2(topic = "Storage Service")
public class StorageService {
    private final MinioClient minioClient;

    @SneakyThrows
    @Autowired
    public StorageService(@Value("${s3.endpoint}") String endpoint,
                          @Value("${s3.accessKey}") String accessKey,
                          @Value("${s3.secretKey}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        for (Bucket bucket : Bucket.values()) {
            if (minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket.getName())
                    .build())) {
                continue;
            }

            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket.getName())
                    .build());
        }
    }

    /**
     * Uploads a file to the given bucket.
     *
     * @param bucket the bucket to upload to
     * @param fileName the name of the file
     * @param data the data to upload
     */
    @SneakyThrows
    public void upload(Bucket bucket, String fileName, byte[] data) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket.getName())
                    .object(fileName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .build());
        } catch (Exception ex) {
            log.error("Failed to upload file to bucket {}: {}", bucket.getName(), ex.getMessage());
            ex.printStackTrace();
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
            return minioClient.getObject(GetObjectArgs.builder()
                            .bucket(bucket.getName())
                            .object(fileName)
                            .build())
                    .readAllBytes();
        } catch (Exception ex) {
            return null;
        }
    }

    @AllArgsConstructor
    @Getter
    public enum Bucket {
        SKINS("mcutils-skins");

        private final String name;
    }
}
