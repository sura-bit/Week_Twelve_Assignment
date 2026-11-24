package org.example.weekttwelveassignment;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiAuthenticationEntryPoint entryPoint,
                                           ApiAccessDeniedHandler deniedHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**",
                                "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST,   "/api/rooms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/rooms/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/*").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(b -> b.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    @Bean
    public SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
    }
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins.split(",")));   // the pages we trust, never "*"
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));            // browser code can read where a 201 went
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
