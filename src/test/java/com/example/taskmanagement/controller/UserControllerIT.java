package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import com.example.taskmanagement.model.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserControllerIT extends ApiTestSupport {
    @Test
    void profileUpdatePatchAndAdminRoleEndpoints() throws Exception {
        var user = account();
        var admin = account();
        var entity = users.findById(admin.id()).orElseThrow();
        entity.setRole(Role.ADMIN);
        users.saveAndFlush(entity);

        assertThat(json(200, "GET", "/users/me", user.token(), null).get("id").asLong()).isEqualTo(user.id());
        assertThat(json(200, "PUT", "/users/me", user.token(),
                Map.of("email", "upd-" + user.name() + "@example.test", "firstName", "U", "lastName", "V"))
                .get("firstName").asText()).isEqualTo("U");
        assertThat(json(200, "PATCH", "/users/me", user.token(), Map.of("lastName", "Patched"))
                .get("lastName").asText()).isEqualTo("Patched");
        assertThat(call("GET", "/users/me", null, null).getStatus()).isEqualTo(401);
        assertThat(call("PUT", "/users/" + admin.id() + "/role", user.token(), Map.of("role", "USER"))
                .getStatus()).isEqualTo(403);
        assertThat(json(200, "PUT", "/users/" + user.id() + "/role", admin.token(), Map.of("role", "ADMIN"))
                .get("role").asText()).isEqualTo("ADMIN");
    }
}
