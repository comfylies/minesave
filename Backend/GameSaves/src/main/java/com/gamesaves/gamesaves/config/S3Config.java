package com.gamesaves.gamesaves.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * AWS S3-compatible client configuration.
 *
 * <p>Supports both MinIO (path-style) and Tencent COS (virtual-hosted-style)
 * via the same S3 protocol. Active when {@code app.storage.type=s3}.
 */
@Configuration
@ConfigurationProperties(prefix = "app.storage.s3")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private boolean pathStyleAccess = true;
    private int presignedUrlExpirationMinutes = 5;

    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3 client — endpoint={}, region={}, bucket={}, pathStyle={}",
                endpoint, region, bucketName, pathStyleAccess);

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build())
                .build();
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }

    public int getPresignedUrlExpirationMinutes() { return presignedUrlExpirationMinutes; }
    public void setPresignedUrlExpirationMinutes(int minutes) { this.presignedUrlExpirationMinutes = minutes; }
}
