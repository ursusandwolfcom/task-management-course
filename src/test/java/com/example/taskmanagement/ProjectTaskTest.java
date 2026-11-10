package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectTaskTest extends ApiTestSupport {
    @Test
    void projectAndTaskCrudKeepMembershipAndDeleteRules() throws Exception {
        var owner = account();
        var member = account();
        long project = project(owner, member);
        long task = task(member, project, member.id());
        assertThat(json(200, "GET", "/projects", member.token(), null).get("content").size()).isPositive();
        assertThat(json(200, "GET", "/projects/" + project, member.token(), null)
                .get("memberIds").get(0).asLong()).isEqualTo(member.id());
        assertThat(json(200, "GET", "/tasks?projectId=" + project, member.token(), null)
                .get("content").size()).isEqualTo(1);
        assertThat(json(200, "GET", "/tasks/" + task, member.token(), null)
                .get("assigneeId").asLong()).isEqualTo(member.id());
        assertThat(call("DELETE", "/projects/" + project, owner.token(), null).getStatus()).isEqualTo(409);
        assertThat(call("PUT", "/projects/" + project, owner.token(), projectBody(List.of()))
                .getStatus()).isEqualTo(409);
        var update = taskBody(project, null, List.of());
        update.put("status", "COMPLETED");
        update.put("priority", "HIGH");
        assertThat(json(200, "PUT", "/tasks/" + task, member.token(), update)
                .get("status").asText()).isEqualTo("COMPLETED");
        json(200, "PUT", "/projects/" + project, owner.token(), projectBody(List.of()));
        assertThat(call("GET", "/tasks/" + task, member.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("DELETE", "/projects/" + project, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/projects/" + project, owner.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    void outsidersAndMembersCannotPerformOwnerOperations() throws Exception {
        var owner = account();
        var member = account();
        var outsider = account();
        long project = project(owner, member);
        long task = task(owner, project, owner.id());
        for (String path : List.of("/projects/" + project, "/tasks/" + task,
                "/tasks?projectId=" + project, "/comments?taskId=" + task,
                "/attachments?taskId=" + task)) {
            assertThat(call("GET", path, outsider.token(), null).getStatus()).as(path).isEqualTo(404);
        }
        assertThat(call("DELETE", "/tasks/" + task, member.token(), null).getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/projects/" + project, member.token(), projectBody(List.of()))
                .getStatus()).isEqualTo(403);
        assertThat(call("POST", "/tasks", outsider.token(), taskBody(project, null, List.of()))
                .getStatus()).isEqualTo(404);
        assertThat(call("PUT", "/tasks/" + task, owner.token(), taskBody(project, outsider.id(), List.of()))
                .getStatus()).isEqualTo(409);
        assertThat(call("POST", "/tasks", owner.token(), taskBody(project, null, List.of(999999999L)))
                .getStatus()).isEqualTo(404);
        long otherProject = project(owner);
        assertThat(call("PUT", "/tasks/" + task, owner.token(), taskBody(otherProject, null, List.of()))
                .getStatus()).isEqualTo(400);
    }

    @Test
    void malformedDatesEnumsMissingRecordsAndUnknownFieldsAreRejected() throws Exception {
        var owner = account();
        var projectBody = new java.util.HashMap<>(projectBody(List.of()));
        projectBody.put("endDate", "2029-01-01");
        assertThat(call("POST", "/projects", owner.token(), projectBody).getStatus()).isEqualTo(400);
        projectBody.put("endDate", "not-a-date");
        assertThat(call("POST", "/projects", owner.token(), projectBody).getStatus()).isEqualTo(400);
        projectBody.put("endDate", "2030-12-31");
        projectBody.put("memberIds", List.of(999999999L));
        assertThat(call("POST", "/projects", owner.token(), projectBody).getStatus()).isEqualTo(404);
        long project = project(owner);
        var body = taskBody(project, null, List.of());
        body.put("priority", "CRITICAL");
        assertThat(call("POST", "/tasks", owner.token(), body).getStatus()).isEqualTo(400);
        body.put("priority", "LOW");
        body.put("status", "FAILED");
        assertThat(call("POST", "/tasks", owner.token(), body).getStatus()).isEqualTo(400);
        body.put("status", "NOT_STARTED");
        body.put("bucket", "INBOX");
        assertThat(call("POST", "/tasks", owner.token(), body).getStatus()).isEqualTo(400);
        assertThat(call("GET", "/tasks/999999999", owner.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/tasks", owner.token(), null).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/tasks", owner.token(), Map.of()).getStatus()).isEqualTo(400);
    }
}
