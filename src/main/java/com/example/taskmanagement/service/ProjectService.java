package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.ProjectFilter;
import com.example.taskmanagement.dto.ProjectRequest;
import com.example.taskmanagement.dto.ProjectResponse;
import com.example.taskmanagement.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectService {
    Project accessible(Long actorId, Long id, boolean ownerOnly);

    Page<ProjectResponse> list(Long actorId, ProjectFilter filter, Pageable pageable);

    Project lock(Long actorId, Long id, boolean ownerOnly);

    ProjectResponse get(Long actorId, Long id);

    ProjectResponse create(Long actorId, ProjectRequest request);

    ProjectResponse update(Long actorId, Long id, ProjectRequest request);

    void delete(Long actorId, Long id);
}
