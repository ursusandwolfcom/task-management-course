package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.ProjectFilter;
import com.example.taskmanagement.dto.ProjectRequest;
import com.example.taskmanagement.dto.ProjectResponse;
import com.example.taskmanagement.exception.EntityNotFoundException;
import com.example.taskmanagement.exception.InsufficientPrivilegesException;
import com.example.taskmanagement.exception.InvalidBusinessDataException;
import com.example.taskmanagement.exception.ResourceConflictException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.ProjectStatus;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.ProjectRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock
    private ProjectRepository projects;
    @Mock
    private TaskRepository tasks;
    @Mock
    private UserService users;
    @Mock
    private ResponseMapper mapper;
    @InjectMocks
    private ProjectServiceImpl service;

    private User owner;
    private User member;
    private User outsider;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = user(1L, Role.USER);
        member = user(2L, Role.USER);
        outsider = user(3L, Role.USER);
        project = new Project();
        ReflectionTestUtils.setField(project, "id", 10L);
        project.setOwner(owner);
        project.setMembers(new HashSet<>(Set.of(member)));
    }

    @Test
    void accessibleAllowsOwnerAndMember() {
        when(users.getUser(1L)).thenReturn(owner);
        when(users.getUser(2L)).thenReturn(member);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        assertThat(service.accessible(1L, 10L, false).getId()).isEqualTo(10L);
        assertThat(service.accessible(2L, 10L, false).getId()).isEqualTo(10L);
    }

    @Test
    void accessibleRejectsOutsiderAndNonOwner() {
        when(users.getUser(2L)).thenReturn(member);
        when(users.getUser(3L)).thenReturn(outsider);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        assertThatThrownBy(() -> service.accessible(2L, 10L, true)).isInstanceOf(InsufficientPrivilegesException.class);
        assertThatThrownBy(() -> service.accessible(3L, 10L, false)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listReturnsProjects() {
        when(users.getUser(1L)).thenReturn(owner);
        when(projects.findAll(any(Specification.class), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(project)));
        when(mapper.toResponse(project)).thenReturn(response());
        assertThat(service.list(1L, new ProjectFilter(null, null, null, null), Pageable.unpaged())
                .getContent()).hasSize(1);
    }

    @Test
    void listRejectsInvalidDateRange() {
        assertThatThrownBy(() -> service.list(1L,
                new ProjectFilter(null, null, LocalDate.of(2030, 12, 31), LocalDate.of(2030, 1, 1)),
                Pageable.unpaged())).isInstanceOf(InvalidBusinessDataException.class);
    }

    @Test
    void getReturnsResponse() {
        when(users.getUser(1L)).thenReturn(owner);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(mapper.toResponse(project)).thenReturn(response());
        assertThat(service.get(1L, 10L).id()).isEqualTo(10L);
    }

    @Test
    void getRejectsOutsider() {
        when(users.getUser(3L)).thenReturn(outsider);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        assertThatThrownBy(() -> service.get(3L, 10L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createPersistsProject() {
        when(users.getUser(1L)).thenReturn(owner);
        when(users.getUser(2L)).thenReturn(member);
        when(projects.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 10L);
            return value;
        });
        when(mapper.toResponse(any(Project.class))).thenReturn(response());
        assertThat(service.create(1L, request()).id()).isEqualTo(10L);
    }

    @Test
    void createRejectsInvalidDateRange() {
        when(users.getUser(1L)).thenReturn(owner);
        var bad = new ProjectRequest("P", "D", LocalDate.of(2030, 12, 31), LocalDate.of(2030, 1, 1),
                ProjectStatus.INITIATED, Set.of());
        assertThatThrownBy(() -> service.create(1L, bad)).isInstanceOf(InvalidBusinessDataException.class);
    }

    @Test
    void updateChangesProject() {
        when(projects.findForUpdate(10L)).thenReturn(Optional.of(project));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(users.getUser(1L)).thenReturn(owner);
        when(users.getUser(2L)).thenReturn(member);
        when(projects.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Project.class))).thenReturn(response());
        assertThat(service.update(1L, 10L, request()).id()).isEqualTo(10L);
    }

    @Test
    void updateRejectsMemberActor() {
        when(projects.findForUpdate(10L)).thenReturn(Optional.of(project));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(users.getUser(2L)).thenReturn(member);
        assertThatThrownBy(() -> service.update(2L, 10L, request()))
                .isInstanceOf(InsufficientPrivilegesException.class);
    }

    @Test
    void deleteRemovesEmptyProject() {
        when(projects.findForUpdate(10L)).thenReturn(Optional.of(project));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(users.getUser(1L)).thenReturn(owner);
        when(tasks.existsByProjectId(10L)).thenReturn(false);
        service.delete(1L, 10L);
    }

    @Test
    void deleteRejectsWhenTasksExist() {
        when(projects.findForUpdate(10L)).thenReturn(Optional.of(project));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(users.getUser(1L)).thenReturn(owner);
        when(tasks.existsByProjectId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L, 10L)).isInstanceOf(ResourceConflictException.class);
    }

    private ProjectRequest request() {
        return new ProjectRequest("P", "D", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31),
                ProjectStatus.INITIATED, Set.of(2L));
    }

    private ProjectResponse response() {
        return new ProjectResponse(10L, "P", "D", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31),
                ProjectStatus.INITIATED, 1L, Set.of(2L));
    }

    private static User user(long id, Role role) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }
}
