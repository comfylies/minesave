package com.gamesaves.gamesaves.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.storage.database-path:../../Database}")
    private String databasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Register /storage/** static resource handler in all modes.
        // In COS mode, this serves locally cached files (cover images, thumbnails)
        // while bulk file storage goes through COS StorageService.
        //
        // Use absolute path string directly instead of toUri().toString() —
        // Path.toUri() percent-encodes spaces in the path (%20), which can cause
        // Spring's ResourceHttpRequestHandler to fail resolving files on Windows
        // when the project path contains spaces (e.g. "claudecode project").
        Path absolutePath = Paths.get(databasePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(absolutePath)) {
            try {
                Files.createDirectories(absolutePath);
            } catch (Exception e) {
                throw new RuntimeException("Cannot create storage directory: " + absolutePath, e);
            }
        }
        String location = "file:" + absolutePath.toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(location);
    }
}
