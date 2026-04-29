package com.retirementmodeler.config;

import com.retirementmodeler.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;

  public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers("/api/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
