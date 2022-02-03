/*
 * ****************************************************
 * Copyright (C) 2020 Ping Identity Corporation
 * All rights reserved.
 *
 * The contents of this file are the property of Ping Identity Corporation.
 * You may not copy or use this file, in either source code or executable
 * form, except in compliance with terms set by Ping Identity Corporation.
 * For further information please contact:
 *
 * Ping Identity Corporation
 * 1001 17th St Suite 100
 * Denver, CO 80202
 * 303.468.2900
 * http://www.pingidentity.com
 * ****************************************************
 */

package com.pingidentity.guides.spa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Import(DevSupportConfiguration.class)
public class ServerApplication
{
    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    // Location of signing keys used to sign the Bearer token JWT, hosted by the PingAccess engine
    @Value("${pingaccess.jwks-url}")
    private String jwksUrl;

    // Value of the Issuer field in the JWT, must match the PingAccess configuration value
    @Value("${pingaccess.jwt.issuer}")
    private String jwtIssuer;

    // Value of the Audience field in the JWT, must match the PingAccess configuration value
    @Value("${pingaccess.jwt.audience}")
    private String jwtAudience;

    // The claim present in the JWT that PingAccess maps the username to
    @Value("${pingaccess.jwt.user-name-claim}")
    private String jwtUserNameClaim;

    // The claim present in the JWT that PingAccess maps the groups to
    @Value("${pingaccess.jwt.groups-claim}")
    private String jwtGroupsClaim;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            WebClient webClient)
    {
        // Changes from the default:
        // - Disable CSRF protection in the application. PingAccess provides CSRF protection by setting the
        //   SameSite attribute on its session cookie as well as requiring the X-Xsrf-Header to be present on
        //   requests to APIs.
        // - Disable logout support. The SPA now sends the browser to /pa/oidc/logout upon logout in the application.
        // - Remove default authentication entry point. This is no longer necessary with PA in the mix.
        http.authorizeExchange(exchanges -> exchanges.pathMatchers("/",
                                                                   "/__parcel_source_root/**",
                                                                   "/index.*",
                                                                   "/favicon.ico").permitAll()
                                                     .anyExchange().authenticated())
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwtConfiguration(jwt, webClient)))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable);
        return http.build();
    }

    private void jwtConfiguration(ServerHttpSecurity.OAuth2ResourceServerSpec.JwtSpec jwt,
                                  WebClient webClient)
    {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUrl)
                                                                      .webClient(webClient)
                                                                      .jwsAlgorithm(SignatureAlgorithm.RS256)
                                                                      .build();
        jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(jwtIssuer),
                                                     new JwtTimestampValidator(),
                                                     new JwtAudienceValidator(jwtAudience)));

        jwt.jwtAuthenticationConverter(createJwtConverter(jwtUserNameClaim, jwtGroupsClaim))
           .jwtDecoder(jwtDecoder);
    }

    // When PingAccess forwards an API call to the SPA, it will create a JWT Bearer token with identity information
    // from the OpenID provider. This token will have the claims configured in PingAccess, in this sample "sub" and
    // "groups".
    private static Converter<Jwt, Mono<JwtAuthenticationToken>> createJwtConverter(String userNameClaim,
                                                                                   String groupsClaim)
    {
        return jwt -> {
            List<String> groups = jwt.getClaimAsStringList(groupsClaim);
            if (groups == null)
            {
                groups = Collections.emptyList();
            }

            List<? extends GrantedAuthority> authorities =
                    groups.stream()
                          .filter(Objects::nonNull)
                          .map(name -> "ROLE_" + name)
                          .map(SimpleGrantedAuthority::new)
                          .collect(Collectors.toList());

            String userName = jwt.getClaimAsString(userNameClaim);

            JwtAuthenticationToken token;
            if (StringUtils.hasText(userName))
            {
                token = new JwtAuthenticationToken(jwt, authorities, userName);
            }
            else
            {
                token = new JwtAuthenticationToken(jwt, authorities);
            }

            return Mono.just(token);
        };
    }

    private static class JwtAudienceValidator implements OAuth2TokenValidator<Jwt>
    {
        private final String expectedAudience;

        public JwtAudienceValidator(String expectedAudience)
        {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token)
        {
            List<String> audienceValue = token.getAudience();
            if (audienceValue == null || !audienceValue.contains(expectedAudience))
            {
                String reason = String.format("Audience '%s' not in '%s'", audienceValue, expectedAudience);
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                                        reason,
                                        "https://tools.ietf.org/html/rfc6750#section-3.1"));
            }

            return OAuth2TokenValidatorResult.success();
        }
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService()
    {
        // Users are now determined by the OpenID provider
        return username -> Mono.empty();
    }

    public static void main(String[] args)
    {
        SpringApplication.run(ServerApplication.class, args);
    }
}
