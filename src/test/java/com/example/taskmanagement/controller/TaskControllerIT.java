package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskControllerIT extends ApiTestSupport {
    @Test
    void listReturnsProjectTasks() throws Exception {
        var owner = account();
        long projectId = project(owner);
        task(owner, projectId, null);
        assertThat(json(200, "GET", "/tasks?projectId=" + projectId, owner.token(), null)
                .get("content").size()).isEqualTo(1);
    }

    @Test
    void listRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/tasks?projectId=1", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void getReturnsTask() throws Exception {
        var owner = account();
        long projectId = project(owner);
        long taskId = task(owner, projectId, null);
        assertThat(json(200, "GET", "/tasks/" + taskId, owner.token(), null).get("projectId").asLong())
                .isEqualTo(projectId);
    }

    @Test
    void getRejectsOutsider() throws Exception {
        var owner = account();
        var outsider = account();
        long taskId = task(owner, project(owner), null);
        assertThat(call("GET", "/tasks/" + taskId, outsider.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    void createPersistsTask() throws Exception {
        var owner = account();
        long projectId = project(owner);
        assertThat(json(201, "POST", "/tasks", owner.token(), taskBody(projectId, owner.id(), List.of()))
                .get("id").asLong()).isPositive();
    }

    @Test
    void createRejectsAnonymous() throws Exception {
        assertThat(call("POST", "/tasks", null, Map.of()).getStatus()).isEqualTo(401);
    }

    @Test
    void updateChangesStatus() throws Exception {
        var owner = account();
        long projectId = project(owner);
        long taskId = task(owner, projectId, null);
        var update = taskBody(projectId, owner.id(), List.of());
        update.put("status", "IN_PROGRESS");
        assertThat(json(200, "PUT", "/tasks/" + taskId, owner.token(), update).get("status").asText())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateRejectsProjectChange() throws Exception {
        var owner = account();
        long first = project(owner);
        long second = project(owner);
        long taskId = task(owner, first, null);
        var update = taskBody(second, owner.id(), List.of());
        assertThat(call("PUT", "/tasks/" + taskId, owner.token(), update).getStatus()).isEqualTo(400);
    }

    @Test
    void deleteRemovesTask() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        assertThat(call("DELETE", "/tasks/" + taskId, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void deleteRejectsAnonymous() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        assertThat(call("DELETE", "/tasks/" + taskId, null, null).getStatus()).isEqualTo(401);
    }
}
