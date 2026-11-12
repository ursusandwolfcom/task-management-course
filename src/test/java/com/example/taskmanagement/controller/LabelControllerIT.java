package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabelControllerIT extends ApiTestSupport {
    @Test
    void listReturnsLabels() throws Exception {
        var user = account();
        String name = "list-" + UUID.randomUUID().toString().replace("-", "");
        json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"));
        assertThat(json(200, "GET", "/labels", user.token(), null).get("content").size()).isPositive();
    }

    @Test
    void listRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/labels", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void createPersistsLabel() throws Exception {
        var user = account();
        String name = "create-" + UUID.randomUUID().toString().replace("-", "");
        assertThat(json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"))
                .get("name").asText()).isEqualTo(name);
    }

    @Test
    void createRejectsAnonymous() throws Exception {
        assertThat(call("POST", "/labels", null, Map.of("name", "x", "color", "#000000")).getStatus())
                .isEqualTo(401);
    }

    @Test
    void updateChangesName() throws Exception {
        var user = account();
        String name = "upd-" + UUID.randomUUID().toString().replace("-", "");
        long id = json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"))
                .get("id").asLong();
        assertThat(json(200, "PUT", "/labels/" + id, user.token(),
                Map.of("name", name + "-2", "color", "#AABBCC")).get("name").asText())
                .isEqualTo(name + "-2");
    }

    @Test
    void updateRejectsMissingLabel() throws Exception {
        var user = account();
        assertThat(call("PUT", "/labels/999999", user.token(), Map.of("name", "missing", "color", "#000000"))
                .getStatus()).isEqualTo(404);
    }

    @Test
    void deleteRemovesLabel() throws Exception {
        var user = account();
        String name = "del-" + UUID.randomUUID().toString().replace("-", "");
        long id = json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"))
                .get("id").asLong();
        assertThat(call("DELETE", "/labels/" + id, user.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void deleteRejectsAnonymous() throws Exception {
        var user = account();
        String name = "anon-" + UUID.randomUUID().toString().replace("-", "");
        long id = json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"))
                .get("id").asLong();
        assertThat(call("DELETE", "/labels/" + id, null, null).getStatus()).isEqualTo(401);
    }
}
