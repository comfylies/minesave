package com.gamesaves.gamesaves.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tencent Cloud COS client configuration.
 * Only active when {@code app.storage.type=cos}.
 */
@Configuration
@ConfigurationProperties(prefix = "app.storage.cos")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cos")
public class CosConfig {

    private static final Logger log = LoggerFactory.getLogger(CosConfig.class);

    private String secretId;
    private String secretKey;
    private String region = "ap-guangzhou";
    private String bucketName;
    private int presignedUrlExpirationMinutes = 5;

    @Bean
    public COSClient cosClient() {
        log.info("Initializing COS client — region={}, bucket={}", region, bucketName);
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(cred, clientConfig);
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public int getPresignedUrlExpirationMinutes() { return presignedUrlExpirationMinutes; }
    public void setPresignedUrlExpirationMinutes(int minutes) { this.presignedUrlExpirationMinutes = minutes; }
}
