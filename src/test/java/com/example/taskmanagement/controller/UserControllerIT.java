package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import com.example.taskmanagement.model.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserControllerIT extends ApiTestSupport {
    @Test
    void getMeReturnsProfile() throws Exception {
        var user = account();
        assertThat(json(200, "GET", "/users/me", user.token(), null).get("id").asLong()).isEqualTo(user.id());
    }

    @Test
    void getMeRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/users/me", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void updateMeReplacesProfile() throws Exception {
        var user = account();
        assertThat(json(200, "PUT", "/users/me", user.token(),
                Map.of("email", "upd-" + user.name() + "@example.test", "firstName", "U", "lastName", "V"))
                .get("firstName").asText()).isEqualTo("U");
    }

    @Test
    void updateMeRejectsAnonymous() throws Exception {
        assertThat(call("PUT", "/users/me", null,
                Map.of("email", "x@example.test", "firstName", "U", "lastName", "V")).getStatus())
                .isEqualTo(401);
    }

    @Test
    void patchMeUpdatesField() throws Exception {
        var user = account();
        assertThat(json(200, "PATCH", "/users/me", user.token(), Map.of("lastName", "Patched"))
                .get("lastName").asText()).isEqualTo("Patched");
    }

    @Test
    void patchMeRejectsAnonymous() throws Exception {
        assertThat(call("PATCH", "/users/me", null, Map.of("lastName", "X")).getStatus()).isEqualTo(401);
    }

    @Test
    void changeRoleAdminSucceeds() throws Exception {
        var user = account();
        var admin = account();
        var entity = users.findById(admin.id()).orElseThrow();
        entity.setRole(Role.ADMIN);
        users.saveAndFlush(entity);
        assertThat(json(200, "PUT", "/users/" + user.id() + "/role", admin.token(), Map.of("role", "ADMIN"))
                .get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void changeRoleRejectsNonAdmin() throws Exception {
        var user = account();
        var other = account();
        assertThat(call("PUT", "/users/" + other.id() + "/role", user.token(), Map.of("role", "USER"))
                .getStatus()).isEqualTo(403);
    }
}
