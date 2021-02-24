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

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@ControllerAdvice
public class Controller
{
    private final Map<String, Todos> todos = new HashMap<>();

    @GetMapping("/todos/{userName}")
    @PreAuthorize("hasRole('sre')")
    public synchronized Mono<Todos> readUserTodos(@PathVariable String userName)
    {
        return Mono.just(getUserTodos(userName));
    }

    @GetMapping("/todos")
    @PreAuthorize("not(hasRole('sre'))")
    public synchronized Mono<Todos> read(Principal user)
    {
        return Mono.just(getUserTodos(user.getName()));
    }

    private Todos getUserTodos(String userName)
    {
        Todos userTodos = todos.get(userName);
        if (userTodos == null)
        {
            return new Todos();
        }

        return userTodos;
    }

    @PostMapping("/todos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("not(hasRole('sre'))")
    public synchronized Mono<Void> create(Principal user, @Valid @RequestBody Todo todo)
    {
        todos.computeIfAbsent(user.getName(), unused -> new Todos()).getTodos().add(todo);
        return Mono.empty();
    }

    private static class Todos
    {
        private List<Todo> todos = new ArrayList<>();

        public List<Todo> getTodos()
        {
            return todos;
        }

        public void setTodos(List<Todo> todos)
        {
            this.todos = todos;
        }
    }

    private static class Todo
    {
        @NotNull
        private UUID id;

        @NotEmpty
        @Size(max = 256)
        private String content;

        public UUID getId()
        {
            return id;
        }

        public void setId(UUID id)
        {
            this.id = id;
        }

        public String getContent()
        {
            return content;
        }

        public void setContent(String content)
        {
            this.content = content;
        }
    }
}
