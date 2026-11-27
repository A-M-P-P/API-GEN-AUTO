package com.apigensetup;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;

public class SetupSecurity {
    @Bean
    SecurityFilterChain setupSecurity(HttpSecurity http, org.springframework.core.env.Environment env) throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(env.getProperty("apigen.cors.allowed-origins", "http://localhost:4200").split(",")));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-Setup-Code"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return http.cors(c -> c.configurationSource(source)).csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/setup/**", "/actuator/health").permitAll().anyRequest().denyAll())
            .build();
    }
}
