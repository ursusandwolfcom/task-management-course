package com.example.taskmanagement.service;

import com.example.taskmanagement.exception.EntityNotFoundException;
import com.example.taskmanagement.dto.LabelRequest;
import com.example.taskmanagement.dto.LabelResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Label;
import com.example.taskmanagement.repository.LabelRepository;
import com.example.taskmanagement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class LabelServiceImpl implements LabelService {
    private final LabelRepository labels;
    private final TaskRepository tasks;
    private final ResponseMapper mapper;

    @Transactional(readOnly = true)
    public Page<LabelResponse> list(Pageable pageable) {
        return labels.findAll(pageable).map(mapper::toResponse);
    }

    public LabelResponse create(LabelRequest request) {
        var label = mapper.toEntity(request);
        return mapper.toResponse(labels.saveAndFlush(label));
    }

    public LabelResponse update(Long id, LabelRequest request) {
        Label label = found(id);
        mapper.update(request, label);
        return mapper.toResponse(labels.saveAndFlush(label));
    }

    public void delete(Long id) {
        Label label = found(id);
        for (var task : tasks.findWithLabel(id)) {
            task.getLabels().removeIf(value -> value.getId().equals(id));
        }
        tasks.flush();
        labels.delete(label);
    }

    private Label found(Long id) {
        return labels.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Label not found"));
    }
}
