package com.example.taskmanagement.service;

import com.example.taskmanagement.exception.ExternalStorageException;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import java.io.InputStream;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DropboxServiceImpl implements DropboxService {

    private final DbxClientV2 client;

    public String upload(String filename, InputStream input) throws IOException {
        try {
            return client.files().uploadBuilder("/course-tasks/" + UUID.randomUUID() + "/" + filename)
                    .uploadAndFinish(input).getId();
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public byte[] download(String id) throws IOException {
        try (var download = client.files().download(id)) {
            byte[] data = download.getInputStream().readNBytes(AttachmentLimits.MAX_BYTES + 1);
            if (data.length > AttachmentLimits.MAX_BYTES) {
                throw unavailable();
            }
            return data;
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public void delete(String id) {
        try {
            client.files().deleteV2(id);
        } catch (DeleteErrorException exception) {
            if (!exception.errorValue.isPathLookup() || !exception.errorValue.getPathLookupValue().isNotFound()) {
                throw unavailable();
            }
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public FileRevision revision(String id) {
        try {
            if (client.files().getMetadata(id) instanceof FileMetadata file && file.getPathLower() != null) {
                return new FileRevision(file.getId(), file.getPathLower(), file.getRev());
            }
            throw unavailable();
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public void restore(FileRevision revision) {
        try {
            var restored = client.files().restore(revision.path(), revision.revision());
            if (!restored.getId().equals(revision.id())) {
                throw unavailable();
            }
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    private ExternalStorageException unavailable() {
        return new ExternalStorageException("Dropbox operation failed");
    }
}
