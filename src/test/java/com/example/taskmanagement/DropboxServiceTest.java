package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteError;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.LookupError;
import com.example.taskmanagement.exception.ExternalStorageException;
import com.example.taskmanagement.service.DropboxService;
import com.example.taskmanagement.service.DropboxService.FileRevision;
import com.example.taskmanagement.service.DropboxServiceImpl;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DropboxServiceTest {
    private final DbxClientV2 client = mock(DbxClientV2.class, RETURNS_DEEP_STUBS);
    private final DropboxService service = new DropboxServiceImpl(client);

    @Test
    void deletedFileRestorationUsesOriginalPathRevisionAndChecksFileIdentity() throws Exception {
        var metadata = mock(FileMetadata.class);
        when(metadata.getId()).thenReturn("id:restore");
        when(metadata.getPathLower()).thenReturn("/course-tasks/original.txt");
        when(metadata.getRev()).thenReturn("original-revision");
        when(client.files().getMetadata("id:restore")).thenReturn(metadata);
        var revision = service.revision("id:restore");
        assertThat(revision).isEqualTo(new FileRevision("id:restore", "/course-tasks/original.txt",
                "original-revision"));
        when(client.files().restore(revision.path(), revision.revision())).thenReturn(metadata);
        service.restore(revision);
        verify(client.files()).restore("/course-tasks/original.txt", "original-revision");
        when(metadata.getId()).thenReturn("id:unexpected");
        assertThatThrownBy(() -> service.restore(revision)).isInstanceOf(ExternalStorageException.class);
        when(client.files().getMetadata("id:private-failure")).thenThrow(new DbxException("secret"));
        assertThatThrownBy(() -> service.revision("id:private-failure"))
                .isInstanceOf(ExternalStorageException.class).hasMessageNotContaining("secret");
    }

    @Test
    void onlyProviderNotFoundIsAnIdempotentDeletion() throws Exception {
        when(client.files().deleteV2("id:gone")).thenThrow(new DeleteErrorException("delete", "request", null,
                DeleteError.pathLookup(LookupError.NOT_FOUND)));
        assertThatCode(() -> service.delete("id:gone")).doesNotThrowAnyException();
        when(client.files().deleteV2("id:locked")).thenThrow(new DeleteErrorException("delete", "request", null,
                DeleteError.pathLookup(LookupError.LOCKED)));
        assertThatThrownBy(() -> service.delete("id:locked")).isInstanceOf(ExternalStorageException.class);
        when(client.files().deleteV2("id:failed")).thenThrow(new DbxException("private provider detail"));
        assertThatThrownBy(() -> service.delete("id:failed")).isInstanceOf(ExternalStorageException.class)
                .hasMessageNotContaining("private provider detail");
    }

    @Test
    void uploadReturnsProviderIdAndDownloadBoundsTheActualBytes() throws Exception {
        var metadata = mock(FileMetadata.class);
        when(metadata.getId()).thenReturn("id:uploaded");
        when(client.files().uploadBuilder(anyString()).uploadAndFinish(any(InputStream.class))).thenReturn(metadata);
        assertThat(service.upload("notes.txt", new ByteArrayInputStream(new byte[] {1, 2})))
                .isEqualTo("id:uploaded");
        when(client.files().download("id:uploaded").getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
        assertThat(service.download("id:uploaded")).containsExactly((byte) 1, (byte) 2);
        when(client.files().download("id:oversize").getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[5 * 1024 * 1024 + 1]));
        assertThatThrownBy(() -> service.download("id:oversize")).isInstanceOf(ExternalStorageException.class);
    }
}
