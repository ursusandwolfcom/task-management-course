package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthControllerIT extends ApiTestSupport {
    @Test
    void registerAndLoginExposeTokenAndCreatedUser() throws Exception {
        String name = "auth" + UUID.randomUUID().toString().replace("-", "");
        var registered = json(201, "POST", "/auth/register", null,
                Map.of("username", name, "password", "password123", "email", name + "@example.test",
                        "firstName", "Ann", "lastName", "Lee"));
        assertThat(registered.get("username").asText()).isEqualTo(name);
        assertThat(registered.has("passwordHash")).isFalse();
        var token = json(200, "POST", "/auth/login", null,
                Map.of("identifier", name, "password", "password123"));
        assertThat(token.get("accessToken").asText()).isNotBlank();
        assertThat(call("POST", "/auth/login", null,
                Map.of("identifier", name, "password", "wrong")).getStatus()).isEqualTo(401);
    }
}
