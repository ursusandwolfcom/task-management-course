package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.CommentRequest;
import com.example.taskmanagement.dto.CommentResponse;
import com.example.taskmanagement.exception.EntityNotFoundException;
import com.example.taskmanagement.exception.InsufficientPrivilegesException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Comment;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.CommentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock
    private CommentRepository comments;
    @Mock
    private TaskService tasks;
    @Mock
    private UserService users;
    @Mock
    private ResponseMapper mapper;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private Clock clock;
    @InjectMocks
    private CommentServiceImpl service;

    private User author;
    private User assignee;
    private User owner;
    private Task task;

    @BeforeEach
    void setUp() {
        author = user(1L, Role.USER);
        assignee = user(2L, Role.USER);
        owner = user(3L, Role.USER);
        Project project = new Project();
        project.setOwner(owner);
        task = new Task();
        ReflectionTestUtils.setField(task, "id", 20L);
        task.setName("Demo");
        task.setProject(project);
        task.setAssignee(assignee);
    }

    @Test
    void createNotifiesAssignee() {
        when(tasks.writable(1L, 20L)).thenReturn(task);
        when(users.getUser(1L)).thenReturn(author);
        when(clock.instant()).thenReturn(Instant.parse("2030-02-10T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", 7L);
            return comment;
        });
        when(mapper.toResponse(any(Comment.class))).thenReturn(
                new CommentResponse(7L, 20L, 1L, "Hello", LocalDateTime.of(2030, 2, 10, 9, 0)));

        assertThat(service.create(1L, new CommentRequest(20L, "Hello")).id()).isEqualTo(7L);
        var email = ArgumentCaptor.forClass(TaskEmail.class);
        verify(events).publishEvent(email.capture());
        assertThat(email.getValue().recipient()).isEqualTo("user2@example.test");
    }

    @Test
    void createRejectsInaccessibleTask() {
        when(tasks.writable(1L, 20L)).thenThrow(new EntityNotFoundException("Task not found"));
        assertThatThrownBy(() -> service.create(1L, new CommentRequest(20L, "Hello")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listReturnsComments() {
        when(tasks.accessible(1L, 20L)).thenReturn(task);
        Comment stored = new Comment();
        ReflectionTestUtils.setField(stored, "id", 7L);
        when(comments.findByTaskId(20L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(stored)));
        when(mapper.toResponse(stored)).thenReturn(
                new CommentResponse(7L, 20L, 1L, "Hello", LocalDateTime.of(2030, 2, 10, 9, 0)));
        assertThat(service.list(1L, 20L, Pageable.unpaged()).getContent()).hasSize(1);
    }

    @Test
    void listRejectsInaccessibleTask() {
        when(tasks.accessible(1L, 20L)).thenThrow(new EntityNotFoundException("Task not found"));
        assertThatThrownBy(() -> service.list(1L, 20L, Pageable.unpaged()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteAllowsProjectOwner() {
        Comment stored = new Comment();
        ReflectionTestUtils.setField(stored, "id", 7L);
        stored.setTask(task);
        stored.setUser(author);
        when(comments.findById(7L)).thenReturn(Optional.of(stored));
        when(tasks.writable(3L, 20L)).thenReturn(task);
        service.delete(3L, 7L);
        verify(comments).delete(stored);
    }

    @Test
    void deleteRejectsStrangerAndMissing() {
        Comment stored = new Comment();
        ReflectionTestUtils.setField(stored, "id", 7L);
        stored.setTask(task);
        stored.setUser(author);
        when(comments.findById(7L)).thenReturn(Optional.of(stored));
        when(tasks.writable(4L, 20L)).thenReturn(task);
        when(users.getUser(4L)).thenReturn(user(4L, Role.USER));
        assertThatThrownBy(() -> service.delete(4L, 7L)).isInstanceOf(InsufficientPrivilegesException.class);

        when(comments.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(1L, 99L)).isInstanceOf(EntityNotFoundException.class);
    }

    private static User user(long id, Role role) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.test");
        user.setRole(role);
        return user;
    }
}
