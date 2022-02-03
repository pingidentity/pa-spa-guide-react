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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A {@link Configuration} bean that defines some configuration to enable local development.
 */
@Configuration
public class DevSupportConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(DevSupportConfiguration.class);

    private final boolean devMode;

    @Autowired
    public DevSupportConfiguration(@Value("${frontend-dev-mode:false}") boolean devMode)
    {
        this.devMode = devMode;

        if (devMode)
        {
            logger.info("Development mode enabled. Requests for static content will be forwarded to " +
                        "https://localhost:1234");
        }
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder)
    {
        if (devMode)
        {
            return builder.routes()
                          .route("dev-server-index", r -> r.path("/").uri("https://localhost:1234"))
                          .route("dev-server", r -> r.path("/index.*").uri("https://localhost:1234"))
                          .route("dev-server-src", r-> r.path("/__parcel_source_root/**")
                                                        .uri("https://localhost:1234"))
                          .build();
        }

        return builder.routes().build();
    }
}
