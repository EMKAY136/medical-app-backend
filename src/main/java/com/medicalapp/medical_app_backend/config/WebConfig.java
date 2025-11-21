package com.medicalapp.medical_app_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty() && !origin.equals("*"))
                .toArray(String[]::new);
        
        System.out.println("\n========== WEB MVC CORS CONFIGURATION ==========");
        System.out.println("Configuring CORS for origins: " + allowedOrigins);
        System.out.println("Parsed origins: " + Arrays.toString(origins));
        
        // CRITICAL FIX: Check if any origin contains wildcard
        for (String origin : origins) {
            if (origin.equals("*")) {
                System.err.println("❌ ERROR: Wildcard '*' found in origins!");
                throw new IllegalArgumentException(
                    "Cannot use '*' wildcard in CORS origins when allowCredentials is true"
                );
            }
        }
        
        // FIXED: Use allowedOriginPatterns instead of allowedOrigins
        // This allows patterns while working with credentials
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "Origin",
                    "X-Requested-With",
                    "Access-Control-Request-Method",
                    "Access-Control-Request-Headers"
                )
                .exposedHeaders("Authorization", "Content-Type", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
                
        System.out.println("✅ WebMvc CORS configured with allowedOriginPatterns");
        System.out.println("✅ Credentials allowed: true");
        System.out.println("✅ Allowed origins: " + Arrays.toString(origins));
        System.out.println("================================================\n");
    }
}