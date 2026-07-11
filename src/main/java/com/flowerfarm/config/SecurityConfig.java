package com.flowerfarm.config;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.auth.FarmUserDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-role barn auth (OWNER / HAND / VIEWER).
 *
 * <p>Enabled when {@code flowerfarm.auth.enabled=true} (profile {@code auth}).
 *
 * <ul>
 *   <li>VIEWER — GET only (except actuator health/info public)</li>
 *   <li>HAND — read + write ops; cannot clear sync history</li>
 *   <li>OWNER — full access including DELETE /api/connectors/history</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "flowerfarm.auth.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain openSecurity(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "flowerfarm.auth.enabled", havingValue = "true")
    public SecurityFilterChain securedSecurity(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Account discovery (usernames only, no passwords)
                        .requestMatchers(HttpMethod.GET, "/api/auth/accounts").permitAll()
                        // VIEWER: read-only
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(
                                FarmRole.OWNER.name(), FarmRole.HAND.name(), FarmRole.VIEWER.name())
                        // Destructive admin
                        .requestMatchers(HttpMethod.DELETE, "/api/connectors/history")
                                .hasRole(FarmRole.OWNER.name())
                        // Mutations require HAND or OWNER
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole(
                                FarmRole.OWNER.name(), FarmRole.HAND.name())
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole(
                                FarmRole.OWNER.name(), FarmRole.HAND.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole(
                                FarmRole.OWNER.name(), FarmRole.HAND.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole(
                                FarmRole.OWNER.name(), FarmRole.HAND.name())
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "flowerfarm.auth.enabled", havingValue = "true")
    public UserDetailsService userDetailsService(FarmUserDirectory directory,
                                                 PasswordEncoder passwordEncoder) {
        List<UserDetails> details = new ArrayList<>();
        for (FarmUser u : directory.listUsers()) {
            details.add(User.builder()
                    .username(u.username())
                    .password(passwordEncoder.encode(u.password()))
                    .roles(u.role().springRole())
                    .build());
        }
        return new InMemoryUserDetailsManager(details);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
