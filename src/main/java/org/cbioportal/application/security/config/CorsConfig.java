package org.cbioportal.application.security.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class CorsConfig {
  @Value("${security.cors.allowed-origins:}")
  private String allowedOrigins;

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    List<String> parsedAllowedOrigins = new ArrayList<>();
    if (allowedOrigins != null && !allowedOrigins.isBlank()) {
      for (String origin : allowedOrigins.split(",")) {
        origin = origin.trim();
        if (!origin.isEmpty() && isValidOrigin(origin)) {
          parsedAllowedOrigins.add(origin);
        }
      }
    }

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (parsedAllowedOrigins.isEmpty()) {
      return source;
    }

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(parsedAllowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "HEAD", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "user-agent",
            "Origin",
            "Accept",
            "X-Requested-With",
            "Content-Type",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "Content-Encoding",
            "X-Proxy-User-Agreement",
            "x-current-url"));
    configuration.setExposedHeaders(List.of("total-count", "sample-count", "elapsed-time"));
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private boolean isValidOrigin(String origin) {
    try {
      URI uri = new URI(origin);
      String scheme = uri.getScheme();
      if (scheme == null) {
        return false;
      }
      scheme = scheme.toLowerCase();
      if (!scheme.equals("http") && !scheme.equals("https")) {
        return false;
      }
      String host = uri.getHost();
      if (host == null || host.isEmpty()) {
        return false;
      }
      // Additional checks can be added here if needed (e.g., whitelist)
      return true;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
