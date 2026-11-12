package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollaborationTest extends ApiTestSupport {
    @Test
    void labelsAreAssignedUpdatedAndDetachedOnDeletion() throws Exception {
        var owner = account();
        String name = "label-" + owner.name();
        long label = json(201, "POST", "/labels", owner.token(), Map.of("name", name, "color", "#12Ab34"))
                .get("id").asLong();
        assertThat(json(200, "GET", "/labels", owner.token(), null).get("content").size()).isPositive();
        var updated = json(200, "PUT", "/labels/" + label, owner.token(),
                Map.of("name", "  " + name + "-new  ", "color", "#FFFFFF"));
        assertThat(updated.get("id").asLong()).isEqualTo(label);
        assertThat(updated.get("name").asText()).isEqualTo(name + "-new");
        assertThat(updated.get("color").asText()).isEqualTo("#FFFFFF");
        long project = project(owner);
        long task = json(201, "POST", "/tasks", owner.token(), taskBody(project, null, List.of(label)))
                .get("id").asLong();
        assertThat(json(200, "GET", "/tasks/" + task, owner.token(), null)
                .get("labelIds").get(0).asLong()).isEqualTo(label);
        assertThat(call("DELETE", "/labels/" + label, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(json(200, "GET", "/tasks/" + task, owner.token(), null).get("labelIds").size()).isZero();
        assertThat(call("PUT", "/labels/" + label, owner.token(),
                Map.of("name", name, "color", "#FFFFFF")).getStatus()).isEqualTo(404);
        assertThat(call("POST", "/labels", owner.token(),
                Map.of("name", " ", "color", "red")).getStatus()).isEqualTo(400);
        json(201, "POST", "/labels", owner.token(), Map.of("name", name, "color", "#FFFFFF"));
        assertThat(call("POST", "/labels", owner.token(),
                Map.of("name", name, "color", "#FFFFFF")).getStatus()).isEqualTo(409);
    }

    @Test
    void commentAuthorsOwnersAndOutsidersHaveDifferentRights() throws Exception {
        var owner = account();
        var author = account();
        var member = account();
        var outsider = account();
        long project = project(owner, author, member);
        long task = task(owner, project, null);
        long comment = json(201, "POST", "/comments", author.token(),
                Map.of("taskId", task, "text", "Ready for review")).get("id").asLong();
        long later = json(201, "POST", "/comments", author.token(),
                Map.of("taskId", task, "text", "Second comment")).get("id").asLong();
        var list = json(200, "GET", "/comments?taskId=" + task, member.token(), null).get("content");
        assertThat(list.get(0).get("id").asLong()).isEqualTo(later);
        assertThat(json(200, "GET", "/comments?taskId=" + task + "&sort=id,asc", member.token(), null)
                .get("content").get(0).get("id").asLong()).isEqualTo(comment);
        assertThat(list.get(0).get("userId").asLong()).isEqualTo(author.id());
        assertThat(list.get(0).get("timestamp").asText()).isEqualTo("2030-02-10T09:00:00");
        assertThat(call("DELETE", "/comments/" + comment, member.token(), null).getStatus()).isEqualTo(403);
        assertThat(call("DELETE", "/comments/" + comment, outsider.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("POST", "/comments", outsider.token(),
                Map.of("taskId", task, "text", "Unauthorized")).getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/comments/" + comment, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("DELETE", "/comments/" + comment, owner.token(), null).getStatus()).isEqualTo(404);
        comment = json(201, "POST", "/comments", author.token(),
                Map.of("taskId", task, "text", "My comment")).get("id").asLong();
        assertThat(call("DELETE", "/comments/" + comment, author.token(), null).getStatus()).isEqualTo(204);
        json(201, "POST", "/comments", owner.token(), Map.of("taskId", task, "text", "Cascade cleanup"));
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/comments?taskId=" + task, owner.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    void healthAndSwaggerDescribeOnlyCourseApi() throws Exception {
        assertThat(json(200, "GET", "/actuator/health", null, null).get("status").asText()).isEqualTo("UP");
        var paths = json(200, "GET", "/v3/api-docs", null, null).get("paths");
        assertThat(paths.has("/tasks")).isTrue();
        assertThat(paths.has("/users/me")).isTrue();
        assertThat(paths.has("/attachments/{id}/download")).isTrue();
        assertThat(paths.toString()).doesNotContain("gantt", "mega", "outbox", "portfolio", "alpha", "session");
        assertThat(call("GET", "/swagger-ui/index.html", null, null).getStatus()).isEqualTo(200);
    }
}
