package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AttachmentTest extends ApiTestSupport {
    @Test
    void archiveRejectsOversizedPagesAndDoesNotReturnPartialFilesOnProviderFailure() throws Exception {
        var owner = account();
        long project = project(owner);
        long task = task(owner, project, null);
        when(dropbox.upload(eq("large.bin"), any())).thenReturn("id:large-1", "id:large-2", "id:large-3",
                "id:large-4", "id:large-5", "id:large-6");
        var file = new MockMultipartFile("file", "large.bin", "application/octet-stream", new byte[] {1});
        for (int index = 1; index <= 6; index++) {
            assertThat(mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                    .isEqualTo(201);
            when(dropbox.download("id:large-" + index)).thenReturn(new byte[5 * 1024 * 1024]);
        }
        assertThat(call("GET", "/attachments?taskId=" + task, owner.token(), null).getStatus()).isEqualTo(413);
        when(dropbox.download("id:large-6")).thenThrow(new java.io.IOException("private transfer failure"));
        var failed = call("GET", "/attachments?taskId=" + task, owner.token(), null);
        assertThat(failed.getStatus()).isEqualTo(502);
        assertThat(failed.getContentType()).doesNotContain("zip");
        assertThat(failed.getContentAsString()).doesNotContain("private transfer failure");
        long emptyTask = task(owner, project, null);
        var empty = call("GET", "/attachments?taskId=" + emptyTask, owner.token(), null);
        assertThat(empty.getStatus()).isEqualTo(200);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(empty.getContentAsByteArray()))) {
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void taskRetrievalReturnsDropboxFilesInPagedZipWithUniqueEntryNames() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("same.txt"), any())).thenReturn("id:zip-first", "id:zip-second");
        var file = new MockMultipartFile("file", "same.txt", "text/plain", new byte[] {1});
        for (int index = 0; index < 2; index++) {
            assertThat(mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                    .isEqualTo(201);
        }
        when(dropbox.download("id:zip-first")).thenReturn(new byte[] {1, 2});
        when(dropbox.download("id:zip-second")).thenReturn(new byte[] {3, 4});
        var response = call("GET", "/attachments?taskId=" + task + "&sort=id,asc", owner.token(), null);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/zip");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            String first = zip.getNextEntry().getName();
            assertThat(first).endsWith("/same.txt");
            assertThat(zip.readAllBytes()).containsExactly((byte) 1, (byte) 2);
            assertThat(zip.getNextEntry().getName()).endsWith("/same.txt").isNotEqualTo(first);
            assertThat(zip.readAllBytes()).containsExactly((byte) 3, (byte) 4);
            assertThat(zip.getNextEntry()).isNull();
        }
        var paged = call("GET", "/attachments?taskId=" + task + "&size=1&page=1&sort=id,asc",
                owner.token(), null);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(paged.getContentAsByteArray()))) {
            assertThat(zip.getNextEntry()).isNotNull();
            assertThat(zip.readAllBytes()).containsExactly((byte) 3, (byte) 4);
            assertThat(zip.getNextEntry()).isNull();
        }
        org.mockito.Mockito.clearInvocations(dropbox);
        assertThat(call("GET", "/attachments?taskId=" + task, outsider.token(), null).getStatus())
                .isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(dropbox);
    }

    @Test
    void uploadListDownloadAndDeleteUsePersistedDropboxId() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("notes.txt"), any())).thenReturn("id:course-file");
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        var attachment = json(200, "GET", "/attachments/metadata?taskId=" + task, owner.token(), null)
                .get("content").get(0);
        assertThat(attachment.get("dropboxFileId").asText()).isEqualTo("id:course-file");
        assertThat(attachment.get("uploadDate").asText()).isEqualTo("2030-02-10T09:00:00");
        when(dropbox.download("id:course-file")).thenReturn(file.getBytes());
        var download = call("GET", "/attachments/" + id + "/download", owner.token(), null);
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).isEqualTo(file.getBytes());
        assertThat(download.getHeader("Content-Disposition")).contains("attachment", "notes.txt");
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(409);
        assertThat(call("DELETE", "/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(204);
        verify(dropbox).delete("id:course-file");
        assertThat(call("GET", "/attachments/" + id + "/download", owner.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void providerFailurePreservesMetadataAndOutsidersCannotAccessFile() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("safe.txt"), any())).thenReturn("id:failure-test");
        var file = new MockMultipartFile("file", "safe.txt", "text/plain", new byte[] {1, 2});
        var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        assertThat(call("GET", "/attachments/" + id + "/download", outsider.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/attachments/" + id, outsider.token(), null).getStatus()).isEqualTo(404);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dropbox operation failed"))
                .when(dropbox).delete("id:failure-test");
        assertThat(call("DELETE", "/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(502);
        assertThat(json(200, "GET", "/attachments/metadata?taskId=" + task, owner.token(), null)
                .get("content").size()).isEqualTo(1);
        when(dropbox.download("id:failure-test")).thenThrow(new java.io.IOException("sensitive provider detail"));
        var failed = call("GET", "/attachments/" + id + "/download", owner.token(), null);
        assertThat(failed.getStatus()).isEqualTo(502);
        assertThat(failed.getContentAsString()).doesNotContain("sensitive provider detail");
    }

    @Test
    void unsafeNamesEmptyFilesAndUnauthorizedUploadsAreRejectedBeforeDropbox() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        for (String name : new String[] {"../secret.txt", "..", "bad\\name.txt", "bad\nname.txt",
                "bad\u007fname.txt", "bad\u0085name.txt", ""}) {
            var file = new MockMultipartFile("file", name, "text/plain", new byte[] {1});
            var result = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
            assertThat(result.getStatus()).as(name).isEqualTo(400);
        }
        var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThat(mvc.perform(multipart("/attachments").file(empty).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(multipart("/attachments").file(empty).param("taskId", "" + task)
                .header("Authorization", "Bearer " + outsider.token())).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(dropbox);
    }
}
