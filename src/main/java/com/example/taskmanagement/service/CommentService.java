package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.CommentRequest;
import com.example.taskmanagement.dto.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponse create(Long actorId, CommentRequest request);

    Page<CommentResponse> list(Long actorId, Long taskId, Pageable pageable);

    void delete(Long actorId, Long id);
}
