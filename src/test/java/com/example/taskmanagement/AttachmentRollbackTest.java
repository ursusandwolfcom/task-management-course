package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.repository.AttachmentRepository;
import com.example.taskmanagement.service.AttachmentService;
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
        var file = new MockMultipartFile("file", "rollback.txt", "text/plain", new byte[] {1});
        var uploaded = new AttachmentResponse[1];
        Long id = transactions.execute(status -> {
            uploaded[0] = upload(owner.id(), task, file);
            status.setRollbackOnly();
            return uploaded[0].id();
        });
        assertThat(repository.existsById(id)).isFalse();
        assertThat(dropbox.deletedIds()).contains(uploaded[0].dropboxFileId());
    }

    @Test
    void deleteRestoresSameRevisionWhenDatabaseTransactionRollsBack() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        var uploaded = upload(owner.id(), task,
                new MockMultipartFile("file", "restore.txt", "text/plain", new byte[] {1, 2}));
        String dropboxId = uploaded.dropboxFileId();
        assertThat(dropbox.deletedIds()).doesNotContain(dropboxId);
        transactions.executeWithoutResult(status -> {
            attachments.delete(owner.id(), uploaded.id());
            status.setRollbackOnly();
        });
        assertThat(repository.existsById(uploaded.id())).isTrue();
        assertThat(dropbox.deletedIds()).contains(dropboxId);
        assertThat(dropbox.restoredRevisions()).extracting(revision -> revision.id()).contains(dropboxId);
        assertThat(repository.findById(uploaded.id()).orElseThrow().getDropboxFileId()).isEqualTo(dropboxId);
    }

    @Test
    void cleanupFailureDoesNotReplaceRollbackOrRestoreMetadata() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        var file = new MockMultipartFile("file", "failure.txt", "text/plain", new byte[] {1});
        assertThatCode(() -> transactions.executeWithoutResult(status -> {
            var saved = upload(owner.id(), task, file);
            dropbox.failDeleteWith(saved.dropboxFileId(), new IllegalStateException("Provider unavailable"));
            status.setRollbackOnly();
        })).doesNotThrowAnyException();
        assertThat(repository.findByTaskId(task, org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
        assertThat(dropbox.deleteAttempts()).isNotEmpty();
    }

    @Test
    void streamCloseFailureRollsBackAndCleansUploadedFile() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
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
        assertThat(dropbox.deletedIds()).isNotEmpty();
        assertThat(repository.findByTaskId(task, org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
    }

    private AttachmentResponse upload(Long actor, Long task, MockMultipartFile file) {
        try {
            return attachments.upload(actor, task, file);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
