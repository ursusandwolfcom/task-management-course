package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.LabelRequest;
import com.example.taskmanagement.dto.LabelResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LabelService {
    Page<LabelResponse> list(Pageable pageable);

    LabelResponse create(LabelRequest request);

    LabelResponse update(Long id, LabelRequest request);

    void delete(Long id);
}
