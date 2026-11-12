package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommentControllerIT extends ApiTestSupport {
    @Test
    void listReturnsComments() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        json(201, "POST", "/comments", owner.token(), Map.of("taskId", taskId, "text", "Listed"));
        assertThat(json(200, "GET", "/comments?taskId=" + taskId, owner.token(), null)
                .get("content").get(0).get("text").asText()).isEqualTo("Listed");
    }

    @Test
    void listRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/comments?taskId=1", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void createPersistsComment() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        assertThat(json(201, "POST", "/comments", owner.token(),
                Map.of("taskId", taskId, "text", "Created")).get("text").asText()).isEqualTo("Created");
    }

    @Test
    void createRejectsAnonymous() throws Exception {
        assertThat(call("POST", "/comments", null, Map.of("taskId", 1, "text", "x")).getStatus())
                .isEqualTo(401);
    }

    @Test
    void deleteRemovesComment() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        long commentId = json(201, "POST", "/comments", owner.token(),
                Map.of("taskId", taskId, "text", "To delete")).get("id").asLong();
        assertThat(call("DELETE", "/comments/" + commentId, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void deleteRejectsStranger() throws Exception {
        var owner = account();
        var stranger = account();
        long taskId = task(owner, project(owner), null);
        long commentId = json(201, "POST", "/comments", owner.token(),
                Map.of("taskId", taskId, "text", "Secret")).get("id").asLong();
        assertThat(call("DELETE", "/comments/" + commentId, stranger.token(), null).getStatus())
                .isIn(403, 404);
    }
}
