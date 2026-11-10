package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.ProfilePatch;
import com.example.taskmanagement.dto.RegisterRequest;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.exception.BadRequestException;
import com.example.taskmanagement.exception.ConflictException;
import com.example.taskmanagement.exception.ForbiddenException;
import com.example.taskmanagement.exception.NotFoundException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwords;
    @Mock
    private ResponseMapper mapper;
    @InjectMocks
    private UserServiceImpl service;

    @Test
    void registerHashesPasswordAndRejectsDuplicates() {
        var request = new RegisterRequest("alice", "password123", "Alice@Example.TEST", "A", "B");
        when(users.existsByEmailIgnoreCase("alice@example.test")).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(passwords.encode("password123")).thenReturn("$2hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });
        when(mapper.toResponse(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getFirstName(), user.getLastName(), user.getRole());
        });

        var response = service.register(request);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.test");
        verify(passwords).encode("password123");

        when(users.existsByUsernameIgnoreCase("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.register(new RegisterRequest("bob", "я".repeat(50),
                "bob@example.test", "B", "O"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void profilePatchAndRoleEnforceRules() {
        User user = user(1L, Role.USER, "one@example.test");
        User admin = user(2L, Role.ADMIN, "admin@example.test");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(User.class))).thenAnswer(invocation -> {
            User value = invocation.getArgument(0);
            return new UserResponse(value.getId(), value.getUsername(), value.getEmail(),
                    value.getFirstName(), value.getLastName(), value.getRole());
        });

        assertThat(service.profile(1L).id()).isEqualTo(1L);
        assertThat(service.patch(1L, new ProfilePatch(null, "Neo", null)).firstName()).isEqualTo("Neo");
        assertThatThrownBy(() -> service.changeRole(1L, 2L, Role.USER)).isInstanceOf(ForbiddenException.class);
        assertThat(service.changeRole(2L, 1L, Role.ADMIN).role()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(() -> service.getUser(99L)).isInstanceOf(NotFoundException.class);
        when(users.existsByEmailIgnoreCase("admin@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.patch(1L, new ProfilePatch("admin@example.test", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    private static User user(long id, Role role, String email) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername("user" + id);
        user.setEmail(email);
        user.setFirstName("F");
        user.setLastName("L");
        user.setRole(role);
        return user;
    }
}
