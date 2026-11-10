package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Attachment;
import com.example.taskmanagement.repository.AttachmentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
@Transactional
public class AttachmentService {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentService.class);
    private static final int MAX_ARCHIVE_BYTES = 25 * 1024 * 1024;
    public record Download(String filename, byte[] content) {
    }

    private final AttachmentRepository attachments;
    private final TaskService tasks;
    private final DropboxService dropbox;
    private final ResponseMapper mapper;
    private final Clock clock;

    @Transactional(rollbackFor = IOException.class)
    public AttachmentResponse upload(Long actorId, Long taskId, MultipartFile file) throws IOException {
        var task = tasks.writable(actorId, taskId);
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > AttachmentLimits.MAX_FILENAME_LENGTH
                || filename.equals(".") || filename.equals("..")
                || filename.chars().anyMatch(value -> Character.isISOControl(value) || "\\/:".indexOf(value) >= 0)
                || file.isEmpty() || file.getSize() > AttachmentLimits.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename or file size (1 B to 5 MB)");
        }
        String externalId;
        try (var input = file.getInputStream()) {
            externalId = dropbox.upload(filename, input);
            compensateOnRollback(externalId, () -> dropbox.delete(externalId));
        }
        var attachment = new Attachment();
        attachment.setTask(task);
        attachment.setDropboxFileId(externalId);
        attachment.setFilename(filename);
        attachment.setUploadDate(LocalDateTime.now(clock));
        return mapper.toResponse(attachments.saveAndFlush(attachment));
    }

    @Transactional(readOnly = true)
    public Page<AttachmentResponse> list(Long actorId, Long taskId, Pageable pageable) {
        tasks.accessible(actorId, taskId);
        return attachments.findByTaskId(taskId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Download archive(Long actorId, Long taskId, Pageable pageable) throws IOException {
        tasks.accessible(actorId, taskId);
        var page = attachments.findByTaskId(taskId, pageable);
        var output = new ByteArrayOutputStream();
        int totalBytes = 0;
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var attachment : page) {
                byte[] content = dropbox.download(attachment.getDropboxFileId());
                totalBytes += content.length;
                if (totalBytes > MAX_ARCHIVE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                            "Archive exceeds 25 MB; request a smaller page size");
                }
                // Stable ID directories avoid duplicate names and preserve original filenames.
                zip.putNextEntry(new ZipEntry(attachment.getId() + "/" + attachment.getFilename()));
                zip.write(content);
                zip.closeEntry();
            }
        }
        return new Download("task-" + taskId + "-page-" + pageable.getPageNumber() + ".zip", output.toByteArray());
    }

    @Transactional(readOnly = true)
    public Download download(Long actorId, Long id) throws IOException {
        Attachment attachment = accessible(actorId, id);
        return new Download(attachment.getFilename(), dropbox.download(attachment.getDropboxFileId()));
    }

    public void delete(Long actorId, Long id) {
        Attachment attachment = found(id);
        tasks.writable(actorId, attachment.getTask().getId());
        var revision = dropbox.revision(attachment.getDropboxFileId());
        compensateOnRollback(attachment.getDropboxFileId(), () -> dropbox.restore(revision));
        dropbox.delete(attachment.getDropboxFileId());
        attachments.delete(attachment);
    }

    private void compensateOnRollback(String externalId, Runnable compensation) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        compensation.run();
                    } catch (RuntimeException failure) {
                        LOG.error("Dropbox rollback compensation failed for {}; manual reconciliation required",
                                externalId);
                    }
                } else if (status == STATUS_UNKNOWN) {
                    LOG.error("Unknown transaction outcome for Dropbox file {}; manual reconciliation required",
                            externalId);
                }
            }
        });
    }

    private Attachment accessible(Long actorId, Long id) {
        Attachment attachment = found(id);
        tasks.accessible(actorId, attachment.getTask().getId());
        return attachment;
    }

    private Attachment found(Long id) {
        return attachments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }
}
