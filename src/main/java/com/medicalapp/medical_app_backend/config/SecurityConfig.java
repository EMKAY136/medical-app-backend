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
        List<String> originList = Arrays.asList(allowedOrigins.split(","));
        System.out.println("Parsed origins: " + originList);
        
        // Use setAllowedOriginPatterns to support wildcards with credentials
        configuration.setAllowedOriginPatterns(originList);
        
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
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        System.out.println("✅ CORS configured with allowedOriginPatterns");
        System.out.println("✅ Credentials allowed: true");
        System.out.println("✅ OPTIONS preflight caching: 3600s");
        System.out.println("==========================================\n");
        
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("\n========== CONFIGURING SECURITY FILTER CHAIN ==========");
        
        http
            // ✅ Enable CORS FIRST - must be before auth
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable CSRF for stateless REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                
                // ✅ OPTIONS preflight requests - MUST come FIRST
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // ✅ Auth endpoints (both /auth and /api/auth patterns)
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/**").permitAll()
                
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/**").permitAll()
                
                // ✅ Public support endpoints
                .requestMatchers("/api/support/**").permitAll()
                
                // ✅ Health check endpoints
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // ✅ Error handling
                .requestMatchers("/error").permitAll()
                .requestMatchers("/").permitAll()
                
                // ✅ WebSocket endpoints - public for initial handshake
                .requestMatchers("/ws/**").permitAll()
                
                // 🔒 Protected admin endpoints
                .requestMatchers("/api/admin/**").authenticated()
                
                // 🔒 Protected user endpoints
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
        System.out.println("✅ CORS enabled");
        System.out.println("✅ JWT filter registered");
        System.out.println("=======================================================\n");
        
        return http.build();
    }
}