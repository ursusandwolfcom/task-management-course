package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.repository.AttachmentRepository;
import com.example.taskmanagement.service.AttachmentService;
import com.example.taskmanagement.service.DropboxService.FileRevision;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

class AttachmentRollbackTest extends ApiTestSupport {
    @Autowired
    private AttachmentService attachments;
    @Autowired
    private AttachmentRepository repository;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void uploadIsCleanedUpWhenTransactionRollsBackAfterFlush() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("rollback.txt"), any())).thenReturn("id:upload-rollback");
        var file = new MockMultipartFile("file", "rollback.txt", "text/plain", new byte[] {1});
        Long id = transactions.execute(status -> {
            var saved = upload(owner.id(), task, file);
            status.setRollbackOnly();
            return saved;
        });
        assertThat(repository.existsById(id)).isFalse();
        verify(dropbox).delete("id:upload-rollback");
    }

    @Test
    void deleteRestoresSameRevisionWhenDatabaseTransactionRollsBack() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("restore.txt"), any())).thenReturn("id:restore-file");
        var revision = new FileRevision("id:restore-file", "/course-tasks/restore.txt", "revision-1");
        when(dropbox.revision("id:restore-file")).thenReturn(revision);
        long id = upload(owner.id(), task,
                new MockMultipartFile("file", "restore.txt", "text/plain", new byte[] {1, 2}));
        verify(dropbox, never()).delete("id:restore-file");
        transactions.executeWithoutResult(status -> {
            attachments.delete(owner.id(), id);
            status.setRollbackOnly();
        });
        assertThat(repository.existsById(id)).isTrue();
        verify(dropbox).delete("id:restore-file");
        verify(dropbox).restore(revision);
        assertThat(repository.findById(id).orElseThrow().getDropboxFileId()).isEqualTo("id:restore-file");
    }

    @Test
    void cleanupFailureDoesNotReplaceRollbackOrRestoreMetadata() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("failure.txt"), any())).thenReturn("id:cleanup-failure");
        doThrow(new IllegalStateException("Provider unavailable")).when(dropbox).delete("id:cleanup-failure");
        var file = new MockMultipartFile("file", "failure.txt", "text/plain", new byte[] {1});
        assertThatCode(() -> transactions.executeWithoutResult(status -> {
            upload(owner.id(), task, file);
            status.setRollbackOnly();
        })).doesNotThrowAnyException();
        assertThat(repository.findByTaskId(task, org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
        verify(dropbox).delete("id:cleanup-failure");
    }

    @Test
    void streamCloseFailureRollsBackAndCleansUploadedFile() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("close.txt"), any())).thenReturn("id:close-failure");
        var file = new MockMultipartFile("file", "close.txt", "text/plain", new byte[] {1}) {
            @Override
            public java.io.InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(new byte[] {1}) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("Stream close failed");
                    }
                };
            }
        };
        assertThatThrownBy(() -> attachments.upload(owner.id(), task, file)).isInstanceOf(IOException.class);
        verify(dropbox).delete("id:close-failure");
        assertThat(repository.findByTaskId(task, org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
    }

    private long upload(Long actor, Long task, MockMultipartFile file) {
        try {
            return attachments.upload(actor, task, file).id();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
