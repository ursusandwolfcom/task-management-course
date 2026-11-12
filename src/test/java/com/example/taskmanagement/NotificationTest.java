package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.service.DeadlineReminderService;
import com.example.taskmanagement.service.TaskEmail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationTest extends ApiTestSupport {
    @Autowired
    private DeadlineReminderService reminders;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private ApplicationEventPublisher events;

    @Test
    void meaningfulEditsNotifyOwnerAndAssigneeButNoOpPutDoesNot() throws Exception {
        var owner = account();
        var assignee = account();
        var editor = account();
        long project = project(owner, assignee, editor);
        long task = task(owner, project, assignee.id());
        awaitMails(1);
        mail.clear();
        var body = taskBody(project, assignee.id(), List.of());
        json(200, "PUT", "/tasks/" + task, editor.token(), body);
        awaitNoMails(300);
        long label = json(201, "POST", "/labels", owner.token(),
                Map.of("name", "notify-" + owner.name(), "color", "#123456")).get("id").asLong();
        var edits = List.<Map.Entry<String, Object>>of(Map.entry("name", "Renamed task"),
                Map.entry("description", "Updated description"), Map.entry("status", "IN_PROGRESS"),
                Map.entry("priority", "HIGH"), Map.entry("dueDate", "2030-02-12"),
                Map.entry("labelIds", List.of(label)));
        for (var edit : edits) {
            body.put(edit.getKey(), edit.getValue());
            mail.clear();
            json(200, "PUT", "/tasks/" + task, editor.token(), body);
            var sent = awaitMails(2);
            assertThat(sent).extracting(message -> message.getTo()[0])
                    .containsExactlyInAnyOrder(owner.name() + "@example.test", assignee.name() + "@example.test");
            assertThat(sent).extracting(SimpleMailMessage::getSubject).containsOnly("Task updated");
        }
    }

    @Test
    void recipientsAreDeduplicatedAndActorAndRolledBackEditsAreExcluded() throws Exception {
        var owner = account();
        var editor = account();
        long project = project(owner, editor);
        long task = task(owner, project, owner.id());
        awaitMails(1);
        mail.clear();
        var body = taskBody(project, owner.id(), List.of());
        body.put("status", "IN_PROGRESS");
        json(200, "PUT", "/tasks/" + task, editor.token(), body);
        var sent = awaitMails(1);
        assertThat(sent.get(0).getTo()).containsExactly(owner.name() + "@example.test");
        mail.clear();
        body.put("priority", "HIGH");
        json(200, "PUT", "/tasks/" + task, owner.token(), body);
        awaitNoMails(300);
        body.put("priority", "LOW");
        transactions.executeWithoutResult(status -> {
            try {
                json(200, "PUT", "/tasks/" + task, editor.token(), body);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            status.setRollbackOnly();
        });
        awaitNoMails(300);
        assertThat(json(200, "GET", "/tasks/" + task, owner.token(), null).get("priority").asText())
                .isEqualTo("HIGH");
    }

    @Test
    void reassignmentNotifiesNewAssigneeAndOwnerOnceEach() throws Exception {
        var owner = account();
        var previous = account();
        var next = account();
        var editor = account();
        long project = project(owner, previous, next, editor);
        long task = task(owner, project, previous.id());
        awaitMails(1);
        mail.clear();
        json(200, "PUT", "/tasks/" + task, editor.token(), taskBody(project, next.id(), List.of()));
        var sent = awaitMails(2);
        assertThat(sent).extracting(message -> message.getTo()[0])
                .containsExactlyInAnyOrder(owner.name() + "@example.test", next.name() + "@example.test");
        assertThat(sent).extracting(SimpleMailMessage::getSubject)
                .containsExactlyInAnyOrder("Task assigned", "Task updated");
    }

    @Test
    void assignmentAndCommentNotifyAssigneeOnlyAfterSuccessfulWrite() throws Exception {
        var owner = account();
        var member = account();
        long project = project(owner, member);
        long task = task(owner, project, member.id());
        var sent = awaitMails(1);
        assertThat(sent.get(0).getTo()).containsExactly(member.name() + "@example.test");
        assertThat(sent.get(0).getSubject()).isEqualTo("Task assigned");
        mail.clear();
        json(201, "POST", "/comments", owner.token(), Map.of("taskId", task, "text", "Please review"));
        sent = awaitMails(1);
        assertThat(sent.get(0).getSubject()).isEqualTo("New task comment");
        mail.clear();
        json(200, "PUT", "/tasks/" + task, owner.token(), taskBody(project, member.id(), List.of()));
        json(201, "POST", "/comments", member.token(), Map.of("taskId", task, "text", "My own comment"));
        awaitNoMails(300);
    }

    @Test
    void dailyRemindersSelectOnlyAssignedOpenTasksDueTodayOrTomorrow() throws Exception {
        var owner = account();
        long project = project(owner);
        for (String date : List.of("2030-02-09", "2030-02-10", "2030-02-11", "2030-02-12")) {
            var body = taskBody(project, owner.id(), List.of());
            body.put("dueDate", date);
            json(201, "POST", "/tasks", owner.token(), body);
        }
        var completed = taskBody(project, owner.id(), List.of());
        completed.put("dueDate", "2030-02-10");
        completed.put("status", "COMPLETED");
        json(201, "POST", "/tasks", owner.token(), completed);
        var unassigned = taskBody(project, null, List.of());
        unassigned.put("dueDate", "2030-02-10");
        json(201, "POST", "/tasks", owner.token(), unassigned);
        awaitMails(5);
        mail.clear();
        reminders.remind();
        var sent = awaitMails(2);
        assertThat(sent).allSatisfy(message -> {
            assertThat(message.getTo()).containsExactly(owner.name() + "@example.test");
            assertThat(message.getSubject()).isEqualTo("Task deadline reminder");
        });
        assertThat(sent).extracting(SimpleMailMessage::getText)
                .anyMatch(text -> text.contains("2030-02-10")).anyMatch(text -> text.contains("2030-02-11"));
    }

    @Test
    void rolledBackChangesNeverSendAndSmtpFailureDoesNotUndoSavedTask() throws Exception {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new TaskEmail("nobody@example.test", "Rollback", "Do not send"));
            status.setRollbackOnly();
        });
        awaitNoMails(300);
        mail.failSends(true);
        var owner = account();
        long task = task(owner, project(owner), owner.id());
        awaitMails(1);
        assertThat(json(200, "GET", "/tasks/" + task, owner.token(), null).get("id").asLong()).isEqualTo(task);
    }
}
