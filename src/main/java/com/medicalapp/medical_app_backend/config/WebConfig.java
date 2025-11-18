package com.medicalapp.medical_app_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        
        System.out.println("\n========== WEB MVC CORS CONFIGURATION ==========");
        System.out.println("Configuring CORS for origins: " + allowedOrigins);
        System.out.println("Parsed origins: " + Arrays.toString(origins));
        
        // FIXED: Use allowedOriginPatterns instead of allowedOrigins
        // This allows wildcards (*) to work with credentials
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)  // Changed from allowedOrigins
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Type", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
                
        System.out.println("✅ WebMvc CORS configured with allowedOriginPatterns");
        System.out.println("✅ Credentials allowed: true");
        System.out.println("================================================\n");
    }
}