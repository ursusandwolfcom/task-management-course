package com.example.taskmanagement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeadlineReminderServiceTest {
    @Mock
    private TaskRepository tasks;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private Clock clock;
    @InjectMocks
    private DeadlineReminderServiceImpl service;

    @Test
    void remindPublishesEmailsForDueTasks() {
        when(clock.instant()).thenReturn(Instant.parse("2030-02-10T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        User assignee = new User();
        assignee.setEmail("assignee@example.test");
        Task task = new Task();
        task.setName("Due soon");
        task.setDueDate(LocalDate.of(2030, 2, 10));
        task.setAssignee(assignee);
        ReflectionTestUtils.setField(task, "id", 1L);
        when(tasks.findReminders(eq(LocalDate.of(2030, 2, 10)), eq(LocalDate.of(2030, 2, 11)), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(task)));

        service.remind();

        var email = ArgumentCaptor.forClass(TaskEmail.class);
        verify(events).publishEvent(email.capture());
        org.assertj.core.api.Assertions.assertThat(email.getValue().recipient()).isEqualTo("assignee@example.test");
        org.assertj.core.api.Assertions.assertThat(email.getValue().subject()).contains("reminder");
    }
}
