package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskControllerIT extends ApiTestSupport {
    @Test
    void taskCrudAndProjectScopedList() throws Exception {
        var owner = account();
        long projectId = project(owner);
        long taskId = json(201, "POST", "/tasks", owner.token(), taskBody(projectId, owner.id(), List.of()))
                .get("id").asLong();
        assertThat(json(200, "GET", "/tasks/" + taskId, owner.token(), null).get("projectId").asLong())
                .isEqualTo(projectId);
        assertThat(json(200, "GET", "/tasks?projectId=" + projectId, owner.token(), null)
                .get("content").size()).isEqualTo(1);
        var update = taskBody(projectId, owner.id(), List.of());
        update.put("status", "IN_PROGRESS");
        assertThat(json(200, "PUT", "/tasks/" + taskId, owner.token(), update).get("status").asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(call("DELETE", "/tasks/" + taskId, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/tasks?projectId=" + projectId, null, null).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/tasks", null, Map.of()).getStatus()).isEqualTo(401);
    }
}
