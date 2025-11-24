package com.medicalapp.medical_app_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    
    @Value("${app.security.cors.allowed-origins:*}")
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
        System.out.println("Allowed origins from config: " + allowedOrigins);
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // ✅ CRITICAL FIX: Always use allowedOriginPatterns instead of allowedOrigins
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty() || allowedOrigins.equals("*")) {
            // Allow all origins with patterns (supports credentials)
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
            System.out.println("✅ Using wildcard pattern: allowing all origins");
        } else {
            // Parse specific origins
            List<String> originList = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList();
            
            configuration.setAllowedOriginPatterns(originList);
            System.out.println("✅ Configured specific origin patterns: " + originList);
        }
        
        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));
        
        // Allow all headers (this is safe with specific origins)
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (required for JWT tokens in Authorization header)
        configuration.setAllowCredentials(true);
        
        // Expose these headers to the client
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type",
            "Content-Disposition",
            "X-Total-Count",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        System.out.println("✅ CORS configured with allowedOriginPatterns (supports credentials)");
        System.out.println("✅ Credentials allowed: true");
        System.out.println("✅ All methods allowed");
        System.out.println("✅ OPTIONS preflight caching: 3600s");
        System.out.println("==========================================\n");
        
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("\n========== CONFIGURING SECURITY FILTER CHAIN ==========");
        
        http
            // ✅ Enable CORS FIRST with our configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable CSRF for stateless REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                
                // ✅ OPTIONS preflight requests - MUST come FIRST
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // ✅ Auth endpoints
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/**").permitAll()
                
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/signup").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/**").permitAll()
                
                // ✅ Public endpoints
                .requestMatchers("/api/support/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/").permitAll()
                
                // ✅ WebSocket - public for initial handshake
                .requestMatchers("/ws/**").permitAll()
                
                // 🔒 Protected endpoints
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .requestMatchers("/api/appointments/**").authenticated()
                .requestMatchers("/api/results/**").authenticated()
                .requestMatchers("/results/**").authenticated()
                
                // 🔒 Everything else requires authentication
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

        // Add JWT filter BEFORE authentication
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        
        System.out.println("✅ Security filter chain configured successfully");
        System.out.println("✅ CORS enabled with allowedOriginPatterns");
        System.out.println("✅ JWT filter registered");
        System.out.println("=======================================================\n");
        
        return http.build();
    }
}