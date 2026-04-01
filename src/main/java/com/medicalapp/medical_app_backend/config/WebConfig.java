package com.medicalapp.medical_app_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS is handled by SecurityConfig.corsConfigurationSource()
    // Removing duplicate CORS config that was causing conflicts
}