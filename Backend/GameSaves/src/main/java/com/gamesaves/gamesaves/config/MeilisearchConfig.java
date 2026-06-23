package com.gamesaves.gamesaves.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MeilisearchConfig {

    private static final Logger log = LoggerFactory.getLogger(MeilisearchConfig.class);

    @Value("${meilisearch.host:http://localhost:7700}")
    private String host;

    @Value("${meilisearch.api-key:gamesaving-master-key-2026}")
    private String apiKey;

    @Bean
    public Client meilisearchClient() {
        Config config = new Config(host, apiKey);
        Client client = new Client(config);
        log.info("Meilisearch client connected to {}", host);
        return client;
    }
}
