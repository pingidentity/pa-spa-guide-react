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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * A {@link WebFilter} that logs the request line and the response status code for a {@link ServerWebExchange}
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter implements WebFilter
{
    private static final Logger logger = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain)
    {
        return chain.filter(exchange)
                    .doFinally(signalType -> logRequest(exchange));
    }

    private void logRequest(ServerWebExchange exchange)
    {
        logger.info("{} {} {}",
                    exchange.getRequest().getMethodValue(),
                    exchange.getRequest().getURI(),
                    exchange.getResponse().getRawStatusCode());
    }
}
