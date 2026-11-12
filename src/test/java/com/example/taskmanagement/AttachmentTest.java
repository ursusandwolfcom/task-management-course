package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentTest extends ApiTestSupport {
    @Test
    void archiveRejectsOversizedPagesAndDoesNotReturnPartialFilesOnProviderFailure() throws Exception {
        var owner = account();
        long project = project(owner);
        long task = task(owner, project, null);
        byte[] large = new byte[5 * 1024 * 1024];
        var file = new MockMultipartFile("file", "large.bin", "application/octet-stream", large);
        String lastId = null;
        for (int index = 1; index <= 6; index++) {
            var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(201);
            lastId = mapper.readTree(response.getContentAsString()).get("dropboxFileId").asText();
        }
        assertThat(call("GET", "/attachments?taskId=" + task, owner.token(), null).getStatus()).isEqualTo(413);
        dropbox.failDownload(lastId);
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
        var first = new MockMultipartFile("file", "same.txt", "text/plain", new byte[] {1, 2});
        var second = new MockMultipartFile("file", "same.txt", "text/plain", new byte[] {3, 4});
        assertThat(mvc.perform(multipart("/attachments").file(first).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                .isEqualTo(201);
        assertThat(mvc.perform(multipart("/attachments").file(second).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                .isEqualTo(201);
        var response = call("GET", "/attachments?taskId=" + task + "&sort=id,asc", owner.token(), null);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/zip");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            String firstName = zip.getNextEntry().getName();
            assertThat(firstName).endsWith("/same.txt");
            assertThat(zip.readAllBytes()).containsExactly((byte) 1, (byte) 2);
            assertThat(zip.getNextEntry().getName()).endsWith("/same.txt").isNotEqualTo(firstName);
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
        int ops = dropbox.operationCount();
        assertThat(call("GET", "/attachments?taskId=" + task, outsider.token(), null).getStatus())
                .isEqualTo(404);
        assertThat(dropbox.operationCount()).isEqualTo(ops);
    }

    @Test
    void uploadListDownloadAndDeleteUsePersistedDropboxId() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        var attachment = json(200, "GET", "/attachments/metadata?taskId=" + task, owner.token(), null)
                .get("content").get(0);
        String dropboxId = attachment.get("dropboxFileId").asText();
        assertThat(dropboxId).startsWith("id:");
        assertThat(attachment.get("uploadDate").asText()).isEqualTo("2030-02-10T09:00:00");
        var download = call("GET", "/attachments/" + id + "/download", owner.token(), null);
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).isEqualTo(file.getBytes());
        assertThat(download.getHeader("Content-Disposition")).contains("attachment", "notes.txt");
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(409);
        assertThat(call("DELETE", "/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(dropbox.deletedIds()).contains(dropboxId);
        assertThat(call("GET", "/attachments/" + id + "/download", owner.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void providerFailurePreservesMetadataAndOutsidersCannotAccessFile() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        var file = new MockMultipartFile("file", "safe.txt", "text/plain", new byte[] {1, 2});
        var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        String dropboxId = mapper.readTree(response.getContentAsString()).get("dropboxFileId").asText();
        assertThat(call("GET", "/attachments/" + id + "/download", outsider.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/attachments/" + id, outsider.token(), null).getStatus()).isEqualTo(404);
        dropbox.failDelete(dropboxId);
        assertThat(call("DELETE", "/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(502);
        assertThat(json(200, "GET", "/attachments/metadata?taskId=" + task, owner.token(), null)
                .get("content").size()).isEqualTo(1);
        dropbox.failDownload(dropboxId);
        var failed = call("GET", "/attachments/" + id + "/download", owner.token(), null);
        assertThat(failed.getStatus()).isEqualTo(502);
        assertThat(failed.getContentAsString()).doesNotContain("private transfer failure");
    }

    @Test
    void unsafeNamesEmptyFilesAndUnauthorizedUploadsAreRejectedBeforeDropbox() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        int ops = dropbox.operationCount();
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
        assertThat(dropbox.operationCount()).isEqualTo(ops);
    }
}
