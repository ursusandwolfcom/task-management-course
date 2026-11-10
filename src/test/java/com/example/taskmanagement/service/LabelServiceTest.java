package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.LabelRequest;
import com.example.taskmanagement.dto.LabelResponse;
import com.example.taskmanagement.exception.NotFoundException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Label;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.repository.LabelRepository;
import com.example.taskmanagement.repository.TaskRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {
    @Mock
    private LabelRepository labels;
    @Mock
    private TaskRepository tasks;
    @Mock
    private ResponseMapper mapper;
    @InjectMocks
    private LabelServiceImpl service;

    @Test
    void createUpdateListAndDeleteDetachFromTasks() {
        var request = new LabelRequest("bug", "#FF0000");
        var label = new Label();
        ReflectionTestUtils.setField(label, "id", 5L);
        label.setName("bug");
        label.setColor("#FF0000");
        when(mapper.toEntity(request)).thenReturn(label);
        when(labels.saveAndFlush(label)).thenReturn(label);
        when(mapper.toResponse(label)).thenReturn(new LabelResponse(5L, "bug", "#FF0000"));
        when(labels.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(label)));
        when(labels.findById(5L)).thenReturn(Optional.of(label));

        assertThat(service.create(request).id()).isEqualTo(5L);
        assertThat(service.list(Pageable.unpaged()).getContent()).hasSize(1);

        var update = new LabelRequest("fixed", "#00FF00");
        when(mapper.toResponse(label)).thenReturn(new LabelResponse(5L, "fixed", "#00FF00"));
        assertThat(service.update(5L, update).name()).isEqualTo("fixed");
        verify(mapper).update(update, label);

        var task = new Task();
        task.setLabels(new HashSet<>(List.of(label)));
        when(tasks.findWithLabel(5L)).thenReturn(List.of(task));
        service.delete(5L);
        assertThat(task.getLabels()).isEmpty();
        verify(labels).delete(label);

        when(labels.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(9L, update)).isInstanceOf(NotFoundException.class);
    }
}
