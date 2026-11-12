package com.example.taskmanagement.service;

import java.io.IOException;
import java.io.InputStream;

public interface DropboxService {
    record FileRevision(String id, String path, String revision) {
    }

    String upload(String filename, InputStream input) throws IOException;

    byte[] download(String id) throws IOException;

    void delete(String id);

    FileRevision revision(String id);

    void restore(FileRevision revision);
}
