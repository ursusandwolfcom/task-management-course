package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.AttachmentResponse;
import java.io.IOException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    record Download(String filename, byte[] content) {
    }

    AttachmentResponse upload(Long actorId, Long taskId, MultipartFile file) throws IOException;

    Page<AttachmentResponse> list(Long actorId, Long taskId, Pageable pageable);

    Download archive(Long actorId, Long taskId, Pageable pageable) throws IOException;

    Download download(Long actorId, Long id) throws IOException;

    void delete(Long actorId, Long id);
}
