package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.config.CosConfig;
import com.gamesaves.gamesaves.exception.StorageException;
import com.gamesaves.gamesaves.service.StorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.DeleteObjectsRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Tencent Cloud COS implementation of StorageService.
 * Active when {@code app.storage.type=cos}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cos")
public class CosStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(CosStorageServiceImpl.class);

    private final COSClient cosClient;
    private final CosConfig cosConfig;

    public CosStorageServiceImpl(COSClient cosClient, CosConfig cosConfig) {
        this.cosClient = cosClient;
        this.cosConfig = cosConfig;
    }

    @PostConstruct
    public void init() {
        String bucket = cosConfig.getBucketName();
        try {
            boolean exists = cosClient.doesBucketExist(bucket);
            log.info("COS storage initialized — bucket={}, exists={}, region={}",
                    bucket, exists, cosConfig.getRegion());
        } catch (Exception e) {
            log.warn("Could not verify COS bucket '{}': {}", bucket, e.getMessage());
        }
    }

    // ── StorageService implementation ───────────────────────────────────

    @Override
    public String store(String key, byte[] data) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            cosClient.putObject(cosConfig.getBucketName(), key,
                    new ByteArrayInputStream(data), metadata);
            log.debug("Stored {} bytes at {}", data.length, key);
            return key;
        } catch (CosClientException e) {
            throw new StorageException("Failed to store " + key, e);
        }
    }

    @Override
    public String storeFromPath(String key, Path localPath) {
        try {
            File file = localPath.toFile();
            PutObjectRequest request = new PutObjectRequest(cosConfig.getBucketName(), key, file);
            cosClient.putObject(request);
            log.debug("Uploaded {} → {}", localPath, key);
            return key;
        } catch (CosClientException e) {
            throw new StorageException("Failed to store " + key + " from " + localPath, e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            COSObject cosObject = cosClient.getObject(cosConfig.getBucketName(), key);
            return IOUtils.toByteArray(cosObject.getObjectContent());
        } catch (CosClientException | IOException e) {
            throw new StorageException("Failed to read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return cosClient.doesObjectExist(cosConfig.getBucketName(), key);
        } catch (CosClientException e) {
            log.warn("Error checking existence of {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            cosClient.deleteObject(cosConfig.getBucketName(), key);
            log.debug("Deleted {}", key);
        } catch (CosClientException e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    @Override
    public void deleteDirectory(String prefix) {
        try {
            // Ensure prefix ends with / for correct prefix matching
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";

            ListObjectsRequest listRequest = new ListObjectsRequest();
            listRequest.setBucketName(cosConfig.getBucketName());
            listRequest.setPrefix(normalizedPrefix);
            listRequest.setMaxKeys(1000);

            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listRequest);
                List<COSObjectSummary> summaries = objectListing.getObjectSummaries();

                if (!summaries.isEmpty()) {
                    DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(cosConfig.getBucketName());
                    List<DeleteObjectsRequest.KeyVersion> keyList = summaries.stream()
                            .map(s -> new DeleteObjectsRequest.KeyVersion(s.getKey()))
                            .toList();
                    deleteRequest.setKeys(keyList);
                    cosClient.deleteObjects(deleteRequest);
                    log.debug("Deleted {} objects under {}", keyList.size(), normalizedPrefix);
                }

                listRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());

        } catch (CosClientException e) {
            throw new StorageException("Failed to delete directory " + prefix, e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expirationMinutes) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60L * 1000L);
            URL url = cosClient.generatePresignedUrl(cosConfig.getBucketName(), key, expiration);
            return url.toString();
        } catch (CosClientException e) {
            throw new StorageException("Failed to generate presigned URL for " + key, e);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        // COS public-read URL format: https://{bucket}.cos.{region}.myqcloud.com/{key}
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosConfig.getBucketName(), cosConfig.getRegion(), key);
    }

    @Override
    public Optional<Path> getLocalPath(String key) {
        try {
            COSObject cosObject = cosClient.getObject(cosConfig.getBucketName(), key);
            // Create temp file with the original extension
            String ext = key.contains(".") ? key.substring(key.lastIndexOf('.')) : ".tmp";
            Path tempFile = Files.createTempFile("cos-dl-", ext);
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                IOUtils.copy(cosObject.getObjectContent(), fos);
            }
            return Optional.of(tempFile);
        } catch (CosClientException | IOException e) {
            log.warn("Failed to download {} to local path: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> listFiles(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            ListObjectsRequest listRequest = new ListObjectsRequest();
            listRequest.setBucketName(cosConfig.getBucketName());
            listRequest.setPrefix(prefix);
            listRequest.setMaxKeys(1000);

            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listRequest);
                for (COSObjectSummary summary : objectListing.getObjectSummaries()) {
                    keys.add(summary.getKey());
                }
                listRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());

        } catch (CosClientException e) {
            throw new StorageException("Failed to list files under " + prefix, e);
        }
        return keys;
    }

    @Override
    public long totalImageSize(String prefix) {
        long total = 0;
        try {
            ListObjectsRequest listRequest = new ListObjectsRequest();
            listRequest.setBucketName(cosConfig.getBucketName());
            listRequest.setPrefix(prefix);
            listRequest.setMaxKeys(1000);

            ObjectListing objectListing;
            do {
                objectListing = cosClient.listObjects(listRequest);
                for (COSObjectSummary summary : objectListing.getObjectSummaries()) {
                    String key = summary.getKey().toLowerCase();
                    if (key.endsWith(".png") || key.endsWith(".jpg") || key.endsWith(".jpeg")
                            || key.endsWith(".gif") || key.endsWith(".webp")) {
                        total += summary.getSize();
                    }
                }
                listRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());

        } catch (CosClientException e) {
            log.warn("Failed to calculate image size for {}: {}", prefix, e.getMessage());
        }
        return total;
    }
}
