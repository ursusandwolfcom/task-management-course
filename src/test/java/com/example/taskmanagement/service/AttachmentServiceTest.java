package com.example.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.exception.AttachmentLimitExceededException;
import com.example.taskmanagement.exception.EntityNotFoundException;
import com.example.taskmanagement.exception.InvalidBusinessDataException;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Attachment;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.repository.AttachmentRepository;
import com.example.taskmanagement.service.DropboxService.FileRevision;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    private Task task;
    private Attachment attachment;

    @BeforeEach
    void setUp() {
        task = new Task();
        ReflectionTestUtils.setField(task, "id", 3L);
        attachment = new Attachment();
        ReflectionTestUtils.setField(attachment, "id", 11L);
        attachment.setTask(task);
        attachment.setDropboxFileId("id:file");
        attachment.setFilename("a.txt");
        attachment.setUploadDate(LocalDateTime.of(2030, 2, 10, 9, 0));
    }

    @Test
    void uploadStoresFile() throws Exception {
        when(tasks.writable(1L, 3L)).thenReturn(task);
        when(clock.instant()).thenReturn(Instant.parse("2030-02-10T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(dropbox.upload(eq("a.txt"), any(InputStream.class))).thenReturn("id:file");
        when(attachments.saveAndFlush(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 11L);
            return value;
        });
        when(mapper.toResponse(any(Attachment.class))).thenReturn(
                new AttachmentResponse(11L, 3L, "a.txt", "id:file", attachment.getUploadDate()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.upload(1L, 3L,
                    new MockMultipartFile("file", "a.txt", "text/plain", new byte[] {1})).id()).isEqualTo(11L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadRejectsInvalidFiles() {
        when(tasks.writable(1L, 3L)).thenReturn(task);
        assertThatThrownBy(() -> service.upload(1L, 3L,
                new MockMultipartFile("file", "../x.txt", "text/plain", new byte[] {1})))
                .isInstanceOf(InvalidBusinessDataException.class);
        assertThatThrownBy(() -> service.upload(1L, 3L,
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(InvalidBusinessDataException.class);
    }

    @Test
    void listReturnsMetadata() {
        when(tasks.accessible(1L, 3L)).thenReturn(task);
        when(attachments.findByTaskId(3L, PageRequest.of(0, 5))).thenReturn(new PageImpl<>(List.of(attachment)));
        when(mapper.toResponse(attachment)).thenReturn(
                new AttachmentResponse(11L, 3L, "a.txt", "id:file", attachment.getUploadDate()));
        assertThat(service.list(1L, 3L, PageRequest.of(0, 5)).getContent()).hasSize(1);
    }

    @Test
    void listRejectsInaccessibleTask() {
        when(tasks.accessible(1L, 3L)).thenThrow(new EntityNotFoundException("Task not found"));
        assertThatThrownBy(() -> service.list(1L, 3L, PageRequest.of(0, 5)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void archiveReturnsZip() throws Exception {
        when(tasks.accessible(1L, 3L)).thenReturn(task);
        when(attachments.findByTaskId(3L, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(attachment)));
        when(dropbox.download("id:file")).thenReturn(new byte[] {1, 2});
        var archive = service.archive(1L, 3L, PageRequest.of(0, 20));
        assertThat(archive.filename()).contains("task-3");
        assertThat(archive.content().length).isPositive();
    }

    @Test
    void archiveRejectsOversizedPayload() throws Exception {
        when(tasks.accessible(1L, 3L)).thenReturn(task);
        when(attachments.findByTaskId(3L, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(attachment)));
        when(dropbox.download("id:file")).thenReturn(new byte[26 * 1024 * 1024]);
        assertThatThrownBy(() -> service.archive(1L, 3L, PageRequest.of(0, 20)))
                .isInstanceOf(AttachmentLimitExceededException.class);
    }

    @Test
    void downloadReturnsBytes() throws Exception {
        when(attachments.findById(11L)).thenReturn(Optional.of(attachment));
        when(tasks.accessible(1L, 3L)).thenReturn(task);
        when(dropbox.download("id:file")).thenReturn(new byte[] {1, 2});
        var download = service.download(1L, 11L);
        assertThat(download.filename()).isEqualTo("a.txt");
        assertThat(download.content()).containsExactly((byte) 1, (byte) 2);
    }

    @Test
    void downloadRejectsMissing() {
        when(attachments.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(1L, 99L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteRemovesAttachment() {
        when(attachments.findById(11L)).thenReturn(Optional.of(attachment));
        when(tasks.writable(1L, 3L)).thenReturn(task);
        when(dropbox.revision("id:file")).thenReturn(new FileRevision("id:file", "/a.txt", "rev"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(1L, 11L);
            verify(dropbox).delete("id:file");
            verify(attachments).delete(attachment);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteRejectsMissing() {
        when(attachments.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(1L, 99L)).isInstanceOf(EntityNotFoundException.class);
    }
}
