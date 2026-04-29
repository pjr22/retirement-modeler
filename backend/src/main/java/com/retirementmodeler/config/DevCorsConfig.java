package com.retirementmodeler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("dev")
public class DevCorsConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    config.addAllowedOrigin("http://localhost:5173");
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.setAllowCredentials(true);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
