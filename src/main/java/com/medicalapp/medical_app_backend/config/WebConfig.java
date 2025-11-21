package com.medicalapp.medical_app_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        System.out.println("\n========== WEB MVC CORS CONFIGURATION ==========");
        System.out.println("Raw allowed origins: " + allowedOrigins);
        
        // Parse and clean origins - remove wildcards
        List<String> originList = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !origin.equals("*"))  // Remove exact "*"
                .collect(Collectors.toList());
        
        System.out.println("Parsed origins (after filtering): " + originList);
        
        // Validate no wildcard-only origins remain
        for (String origin : originList) {
            if (origin.equals("*")) {
                System.err.println("❌ ERROR: Wildcard '*' found!");
                throw new IllegalArgumentException(
                    "Cannot use '*' wildcard in CORS origins when allowCredentials is true. " +
                    "Use specific patterns like 'https://*.vercel.app' instead."
                );
            }
        }
        
        String[] origins = originList.toArray(new String[0]);
        
        // ✅ CRITICAL: Use allowedOriginPatterns (supports patterns like *.vercel.app)
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)  // ✅ Supports wildcard patterns
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
        System.out.println("✅ Patterns supported: https://*.vercel.app");
        System.out.println("✅ Configured origins: " + Arrays.toString(origins));
        System.out.println("================================================\n");
    }
}