package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.exception.BadRequestException;
import com.example.taskmanagement.exception.NotFoundException;
import com.example.taskmanagement.exception.PayloadTooLargeException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Attachment;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.repository.AttachmentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock
    private AttachmentRepository attachments;
    @Mock
    private TaskService tasks;
    @Mock
    private DropboxService dropbox;
    @Mock
    private ResponseMapper mapper;
    @Mock
    private Clock clock;
    @InjectMocks
    private AttachmentServiceImpl service;

    @Test
    void uploadRejectsInvalidFilesAndDownloadArchiveWork() throws Exception {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 3L);
        when(tasks.writable(1L, 3L)).thenReturn(task);
        assertThatThrownBy(() -> service.upload(1L, 3L,
                new MockMultipartFile("file", "../x.txt", "text/plain", new byte[] {1})))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upload(1L, 3L,
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(BadRequestException.class);

        Attachment attachment = new Attachment();
        ReflectionTestUtils.setField(attachment, "id", 11L);
        attachment.setTask(task);
        attachment.setDropboxFileId("id:file");
        attachment.setFilename("a.txt");
        attachment.setUploadDate(LocalDateTime.of(2030, 2, 10, 9, 0));
        when(attachments.findById(11L)).thenReturn(Optional.of(attachment));
        when(tasks.accessible(1L, 3L)).thenReturn(task);
        when(dropbox.download("id:file")).thenReturn(new byte[] {1, 2});

        var download = service.download(1L, 11L);
        assertThat(download.filename()).isEqualTo("a.txt");
        assertThat(download.content()).containsExactly((byte) 1, (byte) 2);

        when(attachments.findByTaskId(3L, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(attachment)));
        var archive = service.archive(1L, 3L, PageRequest.of(0, 20));
        assertThat(archive.filename()).contains("task-3");
        assertThat(archive.content().length).isPositive();

        when(dropbox.download("id:file")).thenReturn(new byte[26 * 1024 * 1024]);
        assertThatThrownBy(() -> service.archive(1L, 3L, PageRequest.of(0, 20)))
                .isInstanceOf(PayloadTooLargeException.class);

        when(attachments.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(1L, 99L)).isInstanceOf(NotFoundException.class);
        when(mapper.toResponse(attachment)).thenReturn(
                new AttachmentResponse(11L, 3L, "a.txt", "id:file", attachment.getUploadDate()));
        when(attachments.findByTaskId(3L, PageRequest.of(0, 5))).thenReturn(new PageImpl<>(List.of(attachment)));
        assertThat(service.list(1L, 3L, PageRequest.of(0, 5)).getContent()).hasSize(1);
    }
}
