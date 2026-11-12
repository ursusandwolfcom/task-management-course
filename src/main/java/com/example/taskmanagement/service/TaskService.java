package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.TaskFilter;
import com.example.taskmanagement.dto.TaskRequest;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {
    Task accessible(Long actorId, Long id);

    Task writable(Long actorId, Long id);

    TaskResponse get(Long actorId, Long id);

    Page<TaskResponse> list(Long actorId, Long projectId, TaskFilter filter, Pageable pageable);

    TaskResponse create(Long actorId, TaskRequest request);

    TaskResponse update(Long actorId, Long id, TaskRequest request);

    void delete(Long actorId, Long id);
}
