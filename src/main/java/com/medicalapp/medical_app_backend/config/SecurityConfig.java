package com.medicalapp.medical_app_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtRequestFilter jwtRequestFilter;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, 
                         JwtRequestFilter jwtRequestFilter) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtRequestFilter = jwtRequestFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // ✅ PRODUCTION READY: Allow specific origins
        configuration.setAllowedOrigins(Arrays.asList(
            // Production - Vercel deployment
            "https://qualitest-admin.vercel.app",
            "https://medical-admin-frontend.vercel.app",
            
            // Local development
            "http://localhost:3000",
            "http://localhost:8000",
            "http://localhost:8080",
            "http://localhost:8081",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:8000",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:8081",
            
            // Your network IPs
            "http://192.168.58.145:3000",
            "http://192.168.58.145:8000",
            "http://192.168.58.145:8080",
            "http://192.168.58.145:8081",

            // React Native specific
            "http://10.0.2.2:8081",
            "exp://localhost:8081",
            "exp://192.168.58.145:8081",
            "capacitor://localhost",
            "ionic://localhost"
        ));
        
        // Also allow all Vercel preview deployments with pattern
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "https://*.vercel.app",
            "https://*.vercel.app/**"
        ));
        
        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));
        
        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Expose these headers to the client
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type",
            "X-Total-Count"
        ));
        
        // How long the response from a pre-flight request can be cached
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS with custom configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable CSRF for stateless REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - MUST be first in order
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/support/status").permitAll()
                .requestMatchers("/api/support/faq").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/").permitAll()
                
                // WebSocket endpoints
                .requestMatchers("/ws/**").permitAll()
                
                // Admin endpoints - require authentication
                .requestMatchers("/api/admin/**").authenticated()
                
                // Other protected endpoints
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .requestMatchers("/api/appointments/**").authenticated()
                .requestMatchers("/api/results/**").authenticated()
                
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            
            // Configure exception handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            
            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        // Add JWT filter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}