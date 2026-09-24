package com.wallettransfer.authentication.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.shared.error.ApiError;
import com.wallettransfer.shared.security.filter.RateLimitFilter;
import com.wallettransfer.shared.security.filter.RequestSizeFilter;
import com.wallettransfer.shared.security.service.RateLimitService;
import com.wallettransfer.shared.security.service.SecurityAuditService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("accessJwtDecoder") JwtDecoder decoder,
            ObjectMapper mapper,
            RateLimitService rateLimits,
            SecurityAuditService securityAudit)
            throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("roles").stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimits, securityAudit, mapper);
        RequestSizeFilter requestSizeFilter = new RequestSizeFilter(mapper);
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentTypeOptions(options -> {})
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(
                                referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(
                                policy -> policy.policy("camera=(), microphone=(), geolocation=(), payment=()"))
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'; "
                                        + "base-uri 'self'; form-action 'self'"))
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                                .authenticationEntryPoint((request, response, exception) -> {
                                    response.setStatus(401);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    ApiError error = new ApiError(
                                            request.getHeader("Authorization") == null
                                                    ? "AUTHENTICATION_REQUIRED"
                                                    : "INVALID_TOKEN",
                                            request.getHeader("Authorization") == null
                                                    ? "Authentication is required"
                                                    : "Token is invalid or expired");
                                    mapper.writeValue(response.getOutputStream(), error);
                                }))
                .addFilterBefore(requestSizeFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiError error = new ApiError("ACCESS_DENIED", "You are not permitted to perform this action");
                    mapper.writeValue(response.getOutputStream(), error);
                }))
                .build();
    }
}
