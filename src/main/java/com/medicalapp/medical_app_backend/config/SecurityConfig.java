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
        CorsConfiguration configuration = new CorsConfiguration();

        if (allowedOrigins == null || allowedOrigins.trim().isEmpty() || allowedOrigins.equals("*")) {
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        } else {
            List<String> originList = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList();
            configuration.setAllowedOriginPatterns(originList);
        }

        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Content-Disposition",
            "X-Total-Count",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth

                // ✅ OPTIONS preflight — must be first
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ✅ Public auth endpoints
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/send-verification").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/verify-email-code").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/send-verification-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/verify-pre-signup-code").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/**").permitAll()

                // ✅ Legacy auth paths
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/signup").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/**").permitAll()

                // ✅ Public support endpoints only
                .requestMatchers(HttpMethod.GET, "/api/support/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/support/faq").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/support/health").permitAll()

                // 🔒 Admin support endpoints — require authentication
                .requestMatchers("/api/support/admin/**").authenticated()

                // 🔒 Patient support endpoints — require authentication
                .requestMatchers("/api/support/chat/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/support/ticket").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/support/chat/send").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/support/tickets").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/support/stats").authenticated()

                // ✅ Other public endpoints
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/").permitAll()

                // ✅ WebSocket handshake
                .requestMatchers("/ws/**").permitAll()

                // 🔒 Protected endpoints
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                .requestMatchers("/api/appointments/**").authenticated()
                .requestMatchers("/api/results/**").authenticated()
                .requestMatchers("/api/support/admin/**").hasAnyAuthority("ADMIN", "DOCTOR")
                .requestMatchers("/results/**").authenticated()

                // 🔒 Everything else
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}