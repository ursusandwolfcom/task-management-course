package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabelControllerIT extends ApiTestSupport {
    @Test
    void labelCrudEndpoints() throws Exception {
        var user = account();
        String name = "it-label-" + UUID.randomUUID().toString().replace("-", "");
        long id = json(201, "POST", "/labels", user.token(), Map.of("name", name, "color", "#112233"))
                .get("id").asLong();
        assertThat(json(200, "GET", "/labels", user.token(), null).get("content").size()).isPositive();
        assertThat(json(200, "PUT", "/labels/" + id, user.token(),
                Map.of("name", name + "-2", "color", "#AABBCC")).get("name").asText())
                .isEqualTo(name + "-2");
        assertThat(call("DELETE", "/labels/" + id, user.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/labels", null, null).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/labels", null, Map.of("name", "x", "color", "#000000")).getStatus())
                .isEqualTo(401);
    }
}
