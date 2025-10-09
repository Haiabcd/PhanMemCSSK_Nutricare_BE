package com.hn.nutricarebe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String[] PUBLIC_POST_ENDPOINTS = {
            "/auths/onboarding",
            "/auths/google/start/**",
            "/auths/google/callback",
            "/ai/plan",
            "/foods/save",
            "/ingredients/save",
            "/conditions/save",
            "/allergies/save",
            "/nutrition-rules/save",
            "/auths/refresh"
    };


    private final String[] PUBLIC_GET_ENDPOINTS = {
            "/auths/google/callback",
            "/foods/**",
            "/foods/search/**",
            "/ingredients/**",
            "/foods/all/**",
            "/ingredients/all/**",
            "/conditions/all/**",
            "/conditions/**",
            "/conditions/search/**",
            "/allergies/all/**",
            "/allergies/search/**",
            "/allergies/**",
            "/nutrition-rules/**"
    };

    private final String[] PUBLIC_DELETE_ENDPOINTS = {
            "/foods/**",
            "/ingredients/**",
            "/conditions/**",
            "/allergies/**"
    };

    private final String[] PUBLIC_PATCH_ENDPOINTS = {
            "/foods/**",
    };


    @Value("${jwt.signerKey}")
    private String signerKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(request ->
                request.requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.DELETE, PUBLIC_DELETE_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.PATCH, PUBLIC_PATCH_ENDPOINTS).permitAll()
                        .anyRequest().authenticated());

        httpSecurity.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwtConfigurer ->
                        jwtConfigurer.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
        );
        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();
    }


    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(signerKey.getBytes(), "HS512");
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();

        // Validate iss = nutricare.com + các mặc định (exp, nbf, iat)
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("nutricare.com");

        // Validate tùy biến: typ phải là "access"
        OAuth2TokenValidator<Jwt> typeIsAccess = jwt -> {
            Object typ = jwt.getClaims().get("typ");
            if ("access".equals(typ)) return OAuth2TokenValidatorResult.success();
            OAuth2Error err = new OAuth2Error("invalid_token", "typ must be 'access'", null);
            return OAuth2TokenValidatorResult.failure(err);
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, typeIsAccess));
        return decoder;
    }

}
