package com.gamesaves.gamesaves.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.storage.database-path:Database}")
    private String databasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only register /storage/** static resource handler in local mode.
        // In COS mode, files are served via StorageService (pre-signed URLs or direct COS URLs).
        if ("local".equals(storageType)) {
            String absolutePath = Paths.get(databasePath).toAbsolutePath().toUri().toString();
            registry.addResourceHandler("/storage/**")
                    .addResourceLocations(absolutePath);
        }
    }
}
