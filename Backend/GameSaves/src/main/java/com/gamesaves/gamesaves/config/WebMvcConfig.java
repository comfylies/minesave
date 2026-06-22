package com.gamesaves.gamesaves.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.storage.database-path:Database}")
    private String databasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve physical storage files for preview/download
        String absolutePath = Paths.get(databasePath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(absolutePath);
    }
}
