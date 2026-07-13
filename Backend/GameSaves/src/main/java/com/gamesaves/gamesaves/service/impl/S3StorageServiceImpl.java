package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.config.S3Config;
import com.gamesaves.gamesaves.exception.StorageException;
import com.gamesaves.gamesaves.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import jakarta.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AWS S3-compatible implementation of StorageService.
 *
 * <p>Works with both MinIO (path-style, local testing) and Tencent COS
 * (virtual-hosted-style S3-compatible endpoint, production).
 *
 * <p>Active when {@code app.storage.type=s3}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    private final S3Client s3Client;
    private final S3Config s3Config;
    private final S3Presigner presigner;

    public S3StorageServiceImpl(S3Client s3Client, S3Config s3Config) {
        this.s3Client = s3Client;
        this.s3Config = s3Config;

        // Separate presigner for presigned URLs (needs its own credential chain)
        this.presigner = S3Presigner.builder()
                .endpointOverride(java.net.URI.create(s3Config.getEndpoint()))
                .region(software.amazon.awssdk.regions.Region.of(s3Config.getRegion()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                s3Config.getAccessKey(), s3Config.getSecretKey())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(s3Config.isPathStyleAccess())
                        .build())
                .build();
    }

    @PostConstruct
    public void init() {
        String bucket = s3Config.getBucketName();
        try {
            HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucket).build();
            s3Client.headBucket(request);
            log.info("S3 storage initialized — bucket={}, endpoint={}, pathStyle={}",
                    bucket, s3Config.getEndpoint(), s3Config.isPathStyleAccess());
        } catch (Exception e) {
            log.warn("Could not verify S3 bucket '{}': {}", bucket, e.getMessage());
            log.warn("Make sure the bucket exists at {} before uploading files.", s3Config.getEndpoint());
        }
    }

    // ── StorageService implementation ───────────────────────────────────

    @Override
    public String store(String key, byte[] data) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(data));
            log.debug("Stored {} bytes at {}", data.length, key);
            return key;
        } catch (S3Exception e) {
            throw new StorageException("Failed to store " + key, e);
        }
    }

    @Override
    public String storeFromPath(String key, Path localPath) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(localPath));
            log.debug("Uploaded {} → {}", localPath, key);
            return key;
        } catch (S3Exception e) {
            throw new StorageException("Failed to store " + key + " from " + localPath, e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("Failed to read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("Error checking existence of {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            log.debug("Deleted {}", key);
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    @Override
    public void deleteDirectory(String prefix) {
        try {
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";

            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(s3Config.getBucketName())
                    .prefix(normalizedPrefix);

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(requestBuilder.build());
                List<ObjectIdentifier> toDelete = response.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .toList();

                if (!toDelete.isEmpty()) {
                    DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                            .bucket(s3Config.getBucketName())
                            .delete(Delete.builder().objects(toDelete).build())
                            .build();
                    s3Client.deleteObjects(deleteRequest);
                    log.debug("Deleted {} objects under {}", toDelete.size(), normalizedPrefix);
                }

                requestBuilder.continuationToken(response.nextContinuationToken());
            } while (response.isTruncated());

        } catch (S3Exception e) {
            throw new StorageException("Failed to delete directory " + prefix, e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expirationMinutes) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(s3Config.getBucketName())
                            .key(key)
                            .build())
                    .build();

            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (S3Exception e) {
            throw new StorageException("Failed to generate presigned URL for " + key, e);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        // 返回预签名 URL 而非直接 COS URL——私有 bucket 的直接 URL 会返回 403
        return generatePresignedUrl(key, s3Config.getPublicUrlExpirationMinutes());
    }

    @Override
    public Optional<Path> getLocalPath(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build();

            String ext = key.contains(".") ? key.substring(key.lastIndexOf('.')) : ".tmp";
            Path tempFile = Files.createTempFile("s3-dl-", ext);

            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
                try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                    stream.transferTo(fos);
                }
            }
            return Optional.of(tempFile);
        } catch (S3Exception | IOException e) {
            log.warn("Failed to download {} to local path: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> listFiles(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(s3Config.getBucketName())
                    .prefix(prefix);

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(requestBuilder.build());
                response.contents().forEach(obj -> keys.add(obj.key()));
                requestBuilder.continuationToken(response.nextContinuationToken());
            } while (response.isTruncated());

        } catch (S3Exception e) {
            throw new StorageException("Failed to list files under " + prefix, e);
        }
        return keys;
    }

    @Override
    public long totalImageSize(String prefix) {
        long total = 0;
        try {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(s3Config.getBucketName())
                    .prefix(prefix);

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(requestBuilder.build());
                for (S3Object obj : response.contents()) {
                    String key = obj.key().toLowerCase();
                    if (key.endsWith(".png") || key.endsWith(".jpg") || key.endsWith(".jpeg")
                            || key.endsWith(".gif") || key.endsWith(".webp")) {
                        total += obj.size();
                    }
                }
                requestBuilder.continuationToken(response.nextContinuationToken());
            } while (response.isTruncated());

        } catch (S3Exception e) {
            log.warn("Failed to calculate image size for {}: {}", prefix, e.getMessage());
        }
        return total;
    }
}
