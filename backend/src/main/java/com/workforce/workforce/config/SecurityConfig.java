package com.workforce.workforce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of("http://localhost:5173"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/employees/**", "/api/departments/**")
                    .hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers("/api/employees/**", "/api/departments/**")
                    .hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/faces/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/ai/attrition/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/attendance").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/leaves").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/leaves/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/tasks").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/tasks").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/payroll").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.POST, "/api/payroll/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.PUT, "/api/payroll/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.POST, "/api/shifts/generate").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.PUT, "/api/shifts/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/shifts/roster").hasAnyRole("ADMIN", "HR", "MANAGER")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter())));
        return http.build();
    }

    private JwtAuthenticationConverter converter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
