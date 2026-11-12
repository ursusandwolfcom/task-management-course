package com.example.taskmanagement.support;

import com.example.taskmanagement.exception.ExternalStorageException;
import com.example.taskmanagement.service.DropboxService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/** Test double for Dropbox: stores bytes in memory; no Mockito. */
public class InMemoryDropboxService implements DropboxService {
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, FileRevision> revisions = new ConcurrentHashMap<>();
    private final List<String> deletedIds = new CopyOnWriteArrayList<>();
    private final List<String> deleteAttempts = new CopyOnWriteArrayList<>();
    private final List<FileRevision> restored = new CopyOnWriteArrayList<>();
    private final Set<String> failDeleteIds = new CopyOnWriteArraySet<>();
    private final Set<String> failDownloadIds = new CopyOnWriteArraySet<>();
    private final Map<String, RuntimeException> deleteFailures = new ConcurrentHashMap<>();
    private int operationCount;

    @Override
    public synchronized String upload(String filename, InputStream input) throws IOException {
        operationCount++;
        String id = "id:" + UUID.randomUUID();
        byte[] data = input.readAllBytes();
        files.put(id, data);
        revisions.put(id, new FileRevision(id, "/course-tasks/" + filename, "rev-1"));
        return id;
    }

    @Override
    public synchronized byte[] download(String id) throws IOException {
        operationCount++;
        if (failDownloadIds.contains(id)) {
            throw new IOException("private transfer failure");
        }
        byte[] data = files.get(id);
        if (data == null) {
            throw new ExternalStorageException("Dropbox operation failed");
        }
        return data;
    }

    @Override
    public synchronized void delete(String id) {
        operationCount++;
        deleteAttempts.add(id);
        RuntimeException configured = deleteFailures.get(id);
        if (configured != null) {
            throw configured;
        }
        if (failDeleteIds.contains(id)) {
            throw new ExternalStorageException("Dropbox operation failed");
        }
        deletedIds.add(id);
        files.remove(id);
    }

    @Override
    public synchronized FileRevision revision(String id) {
        operationCount++;
        FileRevision revision = revisions.get(id);
        if (revision == null) {
            throw new ExternalStorageException("Dropbox operation failed");
        }
        return revision;
    }

    @Override
    public synchronized void restore(FileRevision revision) {
        operationCount++;
        restored.add(revision);
        files.putIfAbsent(revision.id(), new byte[] {1});
        revisions.put(revision.id(), revision);
    }

    public synchronized void reset() {
        files.clear();
        revisions.clear();
        deletedIds.clear();
        deleteAttempts.clear();
        restored.clear();
        failDeleteIds.clear();
        failDownloadIds.clear();
        deleteFailures.clear();
        operationCount = 0;
    }

    public void failDelete(String id) {
        failDeleteIds.add(id);
    }

    public void failDeleteWith(String id, RuntimeException failure) {
        deleteFailures.put(id, failure);
    }

    public void failDownload(String id) {
        failDownloadIds.add(id);
    }

    public List<String> deletedIds() {
        return List.copyOf(deletedIds);
    }

    public List<String> deleteAttempts() {
        return List.copyOf(deleteAttempts);
    }

    public List<FileRevision> restoredRevisions() {
        return List.copyOf(restored);
    }

    public boolean contains(String id) {
        return files.containsKey(id);
    }

    public int operationCount() {
        return operationCount;
    }

    public synchronized void clearOperationLog() {
        deletedIds.clear();
        restored.clear();
        operationCount = 0;
    }
}
