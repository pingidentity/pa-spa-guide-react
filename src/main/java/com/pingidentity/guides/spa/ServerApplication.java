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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.DelegatingServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.ui.LoginPageGeneratingWebFilter;
import reactor.core.publisher.Mono;

import java.time.Duration;

@SpringBootApplication
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Import(DevSupportConfiguration.class)
public class ServerApplication
{
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http)
    {
        // Changes from the default:
        // - Don't redirect unauthenticated requests. The SPA redirects the browser to /login via a user action.
        // - Use a cookie to pass the CSRF token to the JS to include in the X-Xsrf-Token request header field
        // - Don't redirect on logout. The SPA renders the original login page after logout.
        http.authorizeExchange(exchanges -> exchanges.pathMatchers("/",
                                                                   "/__parcel_source_root/**",
                                                                   "/index.*",
                                                                   "/favicon.ico").permitAll()
                                                     .anyExchange().authenticated())
            .formLogin(form -> form.authenticationSuccessHandler(successHandler())
                                   .loginPage("/login"))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
            .logout(logoutSpec -> logoutSpec.logoutSuccessHandler(new HttpStatusReturningServerLogoutSuccessHandler()))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint()));
        enableDefaultLoginPage(http);
        return http.build();
    }

    private ServerAuthenticationSuccessHandler successHandler()
    {
        ServerAuthenticationSuccessHandler setIdleTimeout =
                (filterExchange, authn) -> filterExchange.getExchange()
                                                         .getSession()
                                                         .flatMap(webSession -> {
                                                             webSession.setMaxIdleTime(IDLE_TIMEOUT);
                                                             return Mono.empty();
                                                         });

        return new DelegatingServerAuthenticationSuccessHandler(setIdleTimeout,
                                                                new RedirectServerAuthenticationSuccessHandler("/"));
    }

    private ServerAuthenticationEntryPoint authenticationEntryPoint()
    {
        return new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    private void enableDefaultLoginPage(ServerHttpSecurity http)
    {
        LoginPageGeneratingWebFilter loginPageFilter = new LoginPageGeneratingWebFilter();
        loginPageFilter.setFormLoginEnabled(true);
        http.addFilterAt(loginPageFilter, SecurityWebFiltersOrder.LOGIN_PAGE_GENERATING);
    }

    @Bean
    public MapReactiveUserDetailsService userDetailsService()
    {
        // Sample users
        @SuppressWarnings("deprecation")
        UserDetails bob = User.withDefaultPasswordEncoder()
                              .username("bob")
                              .password("bob")
                              .roles("staff", "sales")
                              .build();
        @SuppressWarnings("deprecation")
        UserDetails alice = User.withDefaultPasswordEncoder()
                                .username("alice")
                                .password("alice")
                                .roles("staff")
                                .build();
        @SuppressWarnings("deprecation")
        UserDetails carol = User.withDefaultPasswordEncoder()
                                .username("carol")
                                .password("carol")
                                .roles("staff", "sre")
                                .build();

        return new MapReactiveUserDetailsService(bob, alice, carol);
    }

    public static void main(String[] args)
    {
        SpringApplication.run(ServerApplication.class, args);
    }
}
