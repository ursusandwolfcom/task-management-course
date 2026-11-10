package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.ProfilePatch;
import com.example.taskmanagement.dto.ProfileRequest;
import com.example.taskmanagement.dto.RegisterRequest;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.User;

public interface UserService {
    UserResponse register(RegisterRequest request);

    User getUser(Long id);

    UserResponse profile(Long id);

    UserResponse update(Long id, ProfileRequest request);

    UserResponse patch(Long id, ProfilePatch request);

    UserResponse changeRole(Long actorId, Long id, Role role);
}
