package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthControllerIT extends ApiTestSupport {
    @Test
    void registerCreatesUser() throws Exception {
        String name = "reg" + UUID.randomUUID().toString().replace("-", "");
        var body = Map.of("username", name, "password", "password123", "email", name + "@example.test",
                "firstName", "Ann", "lastName", "Lee");
        var registered = json(201, "POST", "/auth/register", null, body);
        assertThat(registered.get("username").asText()).isEqualTo(name);
        assertThat(registered.has("passwordHash")).isFalse();
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String name = "dup" + UUID.randomUUID().toString().replace("-", "");
        var body = Map.of("username", name, "password", "password123", "email", name + "@example.test",
                "firstName", "Ann", "lastName", "Lee");
        json(201, "POST", "/auth/register", null, body);
        assertThat(call("POST", "/auth/register", null, body).getStatus()).isEqualTo(409);
    }

    @Test
    void loginReturnsToken() throws Exception {
        var user = account();
        var token = json(200, "POST", "/auth/login", null,
                Map.of("identifier", user.name(), "password", "password123"));
        assertThat(token.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void loginRejectsBadPassword() throws Exception {
        var user = account();
        assertThat(call("POST", "/auth/login", null,
                Map.of("identifier", user.name(), "password", "wrong")).getStatus()).isEqualTo(401);
    }
}
