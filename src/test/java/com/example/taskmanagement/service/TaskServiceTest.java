package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.TaskFilter;
import com.example.taskmanagement.dto.TaskRequest;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.exception.EntityNotFoundException;
import com.example.taskmanagement.exception.InvalidBusinessDataException;
import com.example.taskmanagement.exception.ResourceConflictException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Priority;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.model.TaskStatus;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.AttachmentRepository;
import com.example.taskmanagement.repository.CommentRepository;
import com.example.taskmanagement.repository.LabelRepository;
import com.example.taskmanagement.repository.TaskRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock
    private TaskRepository tasks;
    @Mock
    private ProjectService projects;
    @Mock
    private UserService users;
    @Mock
    private LabelRepository labels;
    @Mock
    private CommentRepository comments;
    @Mock
    private AttachmentRepository attachments;
    @Mock
    private ResponseMapper mapper;
    @Mock
    private ApplicationEventPublisher events;
    @InjectMocks
    private TaskServiceImpl service;

    private User owner;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        owner = user(1L);
        project = new Project();
        ReflectionTestUtils.setField(project, "id", 5L);
        project.setOwner(owner);
        project.setMembers(new HashSet<>());
        task = new Task();
        ReflectionTestUtils.setField(task, "id", 9L);
        task.setProject(project);
        task.setName("T");
        task.setDescription("D");
        task.setPriority(Priority.MEDIUM);
        task.setStatus(TaskStatus.NOT_STARTED);
        task.setLabels(new HashSet<>());
    }

    @Test
    void getReturnsTask() {
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(projects.accessible(1L, 5L, false)).thenReturn(project);
        when(mapper.toResponse(task)).thenReturn(response());
        assertThat(service.get(1L, 9L).id()).isEqualTo(9L);
    }

    @Test
    void getRejectsMissing() {
        when(tasks.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1L, 99L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listReturnsProjectTasks() {
        when(projects.accessible(1L, 5L, false)).thenReturn(project);
        when(tasks.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(mapper.toResponse(task)).thenReturn(response());
        assertThat(service.list(1L, 5L, new TaskFilter(null, null, null, null, null, null, null),
                Pageable.unpaged()).getContent()).hasSize(1);
    }

    @Test
    void listRejectsInvalidDateRange() {
        assertThatThrownBy(() -> service.list(1L, 5L,
                new TaskFilter(null, null, null, null, null, LocalDate.of(2030, 12, 31), LocalDate.of(2030, 1, 1)),
                Pageable.unpaged())).isInstanceOf(InvalidBusinessDataException.class);
    }

    @Test
    void createPersistsTask() {
        when(projects.lock(1L, 5L, false)).thenReturn(project);
        when(labels.findAllById(Set.of())).thenReturn(List.of());
        when(tasks.saveAndFlush(any(Task.class))).thenAnswer(invocation -> {
            Task value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 9L);
            return value;
        });
        when(mapper.toResponse(any(Task.class))).thenReturn(response());
        assertThat(service.create(1L, request(5L, null)).id()).isEqualTo(9L);
    }

    @Test
    void createRejectsOutsideAssignee() {
        User outsider = user(8L);
        when(projects.lock(1L, 5L, false)).thenReturn(project);
        when(users.getUser(8L)).thenReturn(outsider);
        assertThatThrownBy(() -> service.create(1L, request(5L, 8L)))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void updateChangesTask() {
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(projects.lock(1L, 5L, false)).thenReturn(project);
        when(labels.findAllById(Set.of())).thenReturn(List.of());
        when(tasks.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Task.class))).thenReturn(
                new TaskResponse(9L, "T", "D", Priority.MEDIUM, TaskStatus.IN_PROGRESS, null, 5L, null, Set.of()));
        var body = request(5L, null);
        assertThat(service.update(1L, 9L, new TaskRequest(body.name(), body.description(), body.priority(),
                TaskStatus.IN_PROGRESS, body.dueDate(), body.projectId(), body.assigneeId(), body.labelIds()))
                .status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateRejectsProjectChange() {
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(projects.lock(1L, 5L, false)).thenReturn(project);
        assertThatThrownBy(() -> service.update(1L, 9L, request(6L, null)))
                .isInstanceOf(InvalidBusinessDataException.class);
    }

    @Test
    void deleteRemovesTask() {
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(attachments.existsByTaskId(9L)).thenReturn(false);
        when(projects.lock(1L, 5L, true)).thenReturn(project);
        service.delete(1L, 9L);
        verify(comments).deleteByTaskId(9L);
        verify(tasks).delete(task);
    }

    @Test
    void deleteRejectsWhenAttachmentsExist() {
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(projects.lock(1L, 5L, true)).thenReturn(project);
        when(attachments.existsByTaskId(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(ResourceConflictException.class);
    }

    private TaskRequest request(long projectId, Long assigneeId) {
        return new TaskRequest("T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, projectId, assigneeId,
                Set.of());
    }

    private TaskResponse response() {
        return new TaskResponse(9L, "T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, 5L, null, Set.of());
    }

    private static User user(long id) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.test");
        return user;
    }
}
