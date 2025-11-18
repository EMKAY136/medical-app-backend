package com.medicalapp.medical_app_backend.config;

import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

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
        System.out.println("\n========== SECURITY CONFIG CORS ==========");
        System.out.println("Allowed origins from application.yml: " + allowedOrigins);
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse origins from application.yml
        String[] origins = allowedOrigins.split(",");
        
        System.out.println("Parsed origins: " + Arrays.asList(origins));
        
        // FIXED: Use setAllowedOriginPatterns instead of setAllowedOrigins
        // This allows wildcards (*) to work with credentials
        configuration.setAllowedOriginPatterns(Arrays.asList(origins));
        
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
            "X-Total-Count",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        
        // How long the response from a pre-flight request can be cached (1 hour)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        System.out.println("✅ CORS configured with allowedOriginPatterns (supports wildcards)");
        System.out.println("✅ Credentials allowed: true");
        System.out.println("==========================================\n");
        
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("\n========== CONFIGURING SECURITY FILTER CHAIN ==========");
        
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
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/").permitAll()
                
                // WebSocket endpoints - must be public for initial handshake
                .requestMatchers("/ws/**").permitAll()
                
                // Admin endpoints - require authentication
                .requestMatchers("/api/admin/**").authenticated()
                
                // Other protected endpoints
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .requestMatchers("/api/appointments/**").authenticated()
                .requestMatchers("/api/results/**").authenticated()
                .requestMatchers("/results/**").authenticated()
                
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
        
        System.out.println("✅ Security filter chain configured");
        System.out.println("✅ CORS enabled from corsConfigurationSource()");
        System.out.println("✅ JWT filter registered");
        System.out.println("=======================================================\n");
        
        return http.build();
    }
}