package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommentControllerIT extends ApiTestSupport {
    @Test
    void commentCreateListAndDelete() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        long commentId = json(201, "POST", "/comments", owner.token(),
                Map.of("taskId", taskId, "text", "Integration comment")).get("id").asLong();
        assertThat(json(200, "GET", "/comments?taskId=" + taskId, owner.token(), null)
                .get("content").get(0).get("text").asText()).isEqualTo("Integration comment");
        assertThat(call("DELETE", "/comments/" + commentId, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/comments?taskId=" + taskId, null, null).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/comments", null, Map.of("taskId", taskId, "text", "x")).getStatus())
                .isEqualTo(401);
    }
}
