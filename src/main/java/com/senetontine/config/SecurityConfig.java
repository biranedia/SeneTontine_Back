package com.senetontine.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;

    @org.springframework.beans.factory.annotation.Value("${app.cors-origins:*}")
    private String corsOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Necessaire pour tester l'app en mode web (Expo web tourne sur son propre port, ex:
    // localhost:8081, different du port du backend 8080 -> requete "cross-origin" bloquee par
    // defaut par le navigateur). L'app mobile native n'est jamais concernee par le CORS (seuls
    // les navigateurs l'appliquent), donc cette configuration ne change rien pour elle.
    // ATTENTION - permissif car on est encore en developpement (pas d'origine fixe : IP locale
    // qui change, tunnel Cloudflare qui change a chaque relance...) : a restreindre a la vraie
    // origine de production avant toute mise en ligne reelle.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(corsOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {}) // utilise le bean CorsConfigurationSource ci-dessus
                .csrf(csrf -> csrf.disable()) // API stateless consommee par l'app mobile, pas de formulaire HTML
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Page d'erreur interne de Spring : sans ca, toute erreur serveur (500)
                        // etait masquee en 403 vide, rendant les bugs impossibles a diagnostiquer.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // Webhook NabooPay : public, authentifie par signature HMAC (BillingService).
                        .requestMatchers(HttpMethod.POST, "/api/billing/webhook/naboopay").permitAll()
                        // Erreur precedente ici : requestMatchers("OPTIONS", "/**") passe "OPTIONS"
                        // comme un PATTERN d'URL (overload String...), pas comme une methode HTTP -
                        // Spring rejette alors "OPTIONS" au demarrage car un pattern doit commencer
                        // par "/". Il faut l'overload (HttpMethod, pattern) pour cibler la methode.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // preflight CORS du navigateur
                        // Autorisations metier (createur d'une tontine, admin plateforme...)
                        // verifiees dans chaque service : elles dependent des donnees.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
