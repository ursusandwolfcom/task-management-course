package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.example.taskmanagement.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentControllerIT extends ApiTestSupport {
    @Test
    void metadataListsAttachments() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        upload(owner.token(), taskId, "notes.txt", new byte[] {1, 2, 3});
        assertThat(json(200, "GET", "/attachments/metadata?taskId=" + taskId, owner.token(), null)
                .get("content").get(0).get("filename").asText()).isEqualTo("notes.txt");
    }

    @Test
    void metadataRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/attachments/metadata?taskId=1", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void archiveReturnsZip() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        upload(owner.token(), taskId, "a.txt", new byte[] {9});
        var zip = call("GET", "/attachments?taskId=" + taskId, owner.token(), null);
        assertThat(zip.getStatus()).isEqualTo(200);
        assertThat(zip.getContentType()).isEqualTo("application/zip");
    }

    @Test
    void archiveRejectsAnonymous() throws Exception {
        assertThat(call("GET", "/attachments?taskId=1", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void downloadReturnsBytes() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        long id = upload(owner.token(), taskId, "bin.txt", new byte[] {9, 8, 7});
        var download = call("GET", "/attachments/" + id + "/download", owner.token(), null);
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).containsExactly((byte) 9, (byte) 8, (byte) 7);
    }

    @Test
    void downloadRejectsMissingAttachment() throws Exception {
        var owner = account();
        assertThat(call("GET", "/attachments/999999/download", owner.token(), null).getStatus())
                .isEqualTo(404);
    }

    @Test
    void uploadStoresFile() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        long id = upload(owner.token(), taskId, "up.txt", new byte[] {4, 5});
        assertThat(id).isPositive();
    }

    @Test
    void uploadRejectsAnonymous() throws Exception {
        var file = new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {1});
        var response = mvc.perform(multipart("/attachments").file(file).param("taskId", "1"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void deleteRemovesAttachmentAndDropboxFile() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        var file = new MockMultipartFile("file", "del.txt", "text/plain", new byte[] {1});
        var uploaded = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + taskId)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(uploaded.getStatus()).isEqualTo(201);
        long id = mapper.readTree(uploaded.getContentAsString()).get("id").asLong();
        String dropboxId = mapper.readTree(uploaded.getContentAsString()).get("dropboxFileId").asText();
        assertThat(call("DELETE", "/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(dropbox.deletedIds()).contains(dropboxId);
    }

    @Test
    void deleteRejectsAnonymous() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        long id = upload(owner.token(), taskId, "anon.txt", new byte[] {1});
        assertThat(call("DELETE", "/attachments/" + id, null, null).getStatus()).isEqualTo(401);
    }

    private long upload(String token, long taskId, String filename, byte[] bytes) throws Exception {
        var file = new MockMultipartFile("file", filename, "text/plain", bytes);
        var uploaded = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + taskId)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(uploaded.getStatus()).as(uploaded.getContentAsString()).isEqualTo(201);
        return mapper.readTree(uploaded.getContentAsString()).get("id").asLong();
    }
}
