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
        // Register /storage/** static resource handler in all modes.
        // In COS mode, this serves locally cached files (cover images, thumbnails)
        // while bulk file storage goes through COS StorageService.
        String absolutePath = Paths.get(databasePath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(absolutePath);
    }
}
