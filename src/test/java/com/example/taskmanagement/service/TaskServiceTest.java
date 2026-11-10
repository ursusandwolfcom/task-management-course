package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.TaskFilter;
import com.example.taskmanagement.dto.TaskRequest;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.exception.BadRequestException;
import com.example.taskmanagement.exception.ConflictException;
import com.example.taskmanagement.exception.NotFoundException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
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

    @Test
    void createAssignsAndDeleteGuardsAttachments() {
        User owner = user(1L);
        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", 5L);
        project.setOwner(owner);
        project.setMembers(new HashSet<>());
        when(projects.lock(1L, 5L, false)).thenReturn(project);
        when(labels.findAllById(Set.of())).thenReturn(List.of());
        when(tasks.saveAndFlush(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 9L);
            return task;
        });
        when(mapper.toResponse(any(Task.class))).thenReturn(
                new TaskResponse(9L, "T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, 5L, null, Set.of()));

        var request = new TaskRequest("T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, 5L, null, Set.of());
        assertThat(service.create(1L, request).id()).isEqualTo(9L);

        Task stored = new Task();
        ReflectionTestUtils.setField(stored, "id", 9L);
        stored.setProject(project);
        stored.setName("T");
        stored.setLabels(new HashSet<>());
        when(tasks.findById(9L)).thenReturn(Optional.of(stored));
        when(attachments.existsByTaskId(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(ConflictException.class);

        when(attachments.existsByTaskId(9L)).thenReturn(false);
        when(projects.lock(1L, 5L, true)).thenReturn(project);
        service.delete(1L, 9L);
        verify(comments).deleteByTaskId(9L);
        verify(tasks).delete(stored);

        assertThatThrownBy(() -> service.list(1L, 5L,
                new TaskFilter(null, null, null, null, null, LocalDate.of(2030, 12, 31), LocalDate.of(2030, 1, 1)),
                Pageable.unpaged())).isInstanceOf(BadRequestException.class);
        when(tasks.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1L, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateRejectsProjectChangeAndOutsideAssignee() {
        User owner = user(1L);
        User outsider = user(8L);
        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", 5L);
        project.setOwner(owner);
        project.setMembers(new HashSet<>());
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 9L);
        task.setProject(project);
        task.setName("T");
        task.setDescription("D");
        task.setPriority(Priority.MEDIUM);
        task.setStatus(TaskStatus.NOT_STARTED);
        task.setLabels(new HashSet<>());
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(projects.lock(1L, 5L, false)).thenReturn(project);

        assertThatThrownBy(() -> service.update(1L, 9L,
                new TaskRequest("T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, 6L, null, Set.of())))
                .isInstanceOf(BadRequestException.class);

        when(users.getUser(8L)).thenReturn(outsider);
        assertThatThrownBy(() -> service.update(1L, 9L,
                new TaskRequest("T", "D", Priority.MEDIUM, TaskStatus.NOT_STARTED, null, 5L, 8L, Set.of())))
                .isInstanceOf(ConflictException.class);
    }

    private static User user(long id) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.test");
        return user;
    }
}
