package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectControllerIT extends ApiTestSupport {
    @Test
    void listReturnsAccessibleProjects() throws Exception {
        var owner = account();
        project(owner);
        assertThat(json(200, "GET", "/projects?name=Course", owner.token(), null).get("content").size())
                .isPositive();
    }

    @Test
    void listRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/projects", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void getReturnsProject() throws Exception {
        var owner = account();
        long id = project(owner);
        assertThat(json(200, "GET", "/projects/" + id, owner.token(), null).get("name").asText())
                .isEqualTo("Course project");
    }

    @Test
    void getRejectsOutsider() throws Exception {
        var owner = account();
        var outsider = account();
        long id = project(owner);
        assertThat(call("GET", "/projects/" + id, outsider.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    void createPersistsProject() throws Exception {
        var owner = account();
        var member = account();
        assertThat(json(201, "POST", "/projects", owner.token(), projectBody(List.of(member.id())))
                .get("id").asLong()).isPositive();
    }

    @Test
    void createRejectsAnonymous() throws Exception {
        assertThat(call("POST", "/projects", null, projectBody(List.of())).getStatus()).isEqualTo(401);
    }

    @Test
    void updateChangesStatus() throws Exception {
        var owner = account();
        var member = account();
        long id = json(201, "POST", "/projects", owner.token(), projectBody(List.of(member.id())))
                .get("id").asLong();
        var updated = new HashMap<>(projectBody(List.of(member.id())));
        updated.put("status", "IN_PROGRESS");
        assertThat(json(200, "PUT", "/projects/" + id, owner.token(), updated).get("status").asText())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateRejectsMember() throws Exception {
        var owner = account();
        var member = account();
        long id = json(201, "POST", "/projects", owner.token(), projectBody(List.of(member.id())))
                .get("id").asLong();
        assertThat(call("PUT", "/projects/" + id, member.token(), projectBody(List.of(member.id())))
                .getStatus()).isEqualTo(403);
    }

    @Test
    void deleteRemovesEmptyProject() throws Exception {
        var owner = account();
        long id = project(owner);
        assertThat(call("DELETE", "/projects/" + id, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void deleteRejectsWhenTasksExist() throws Exception {
        var owner = account();
        long id = project(owner);
        task(owner, id, null);
        assertThat(call("DELETE", "/projects/" + id, owner.token(), null).getStatus()).isEqualTo(409);
    }
}
