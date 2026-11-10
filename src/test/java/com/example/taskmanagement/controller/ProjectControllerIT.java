package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectControllerIT extends ApiTestSupport {
    @Test
    void projectCrudAndListRequireAuthentication() throws Exception {
        var owner = account();
        var member = account();
        long id = json(201, "POST", "/projects", owner.token(), projectBody(List.of(member.id())))
                .get("id").asLong();
        assertThat(json(200, "GET", "/projects/" + id, owner.token(), null).get("name").asText())
                .isEqualTo("Course project");
        assertThat(json(200, "GET", "/projects?name=Course", owner.token(), null).get("content").size())
                .isPositive();
        var updated = new java.util.HashMap<>(projectBody(List.of(member.id())));
        updated.put("status", "IN_PROGRESS");
        assertThat(json(200, "PUT", "/projects/" + id, owner.token(), updated).get("status").asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(call("DELETE", "/projects/" + id, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/projects", null, null).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/projects", null, projectBody(List.of())).getStatus()).isEqualTo(401);
    }
}
