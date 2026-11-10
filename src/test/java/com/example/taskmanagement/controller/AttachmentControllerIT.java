package com.example.taskmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.example.taskmanagement.ApiTestSupport;
import com.example.taskmanagement.service.DropboxService.FileRevision;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentControllerIT extends ApiTestSupport {
    @Test
    void uploadMetadataDownloadArchiveAndDelete() throws Exception {
        var owner = account();
        long taskId = task(owner, project(owner), null);
        when(dropbox.upload(eq("notes.txt"), any())).thenReturn("id:it-file");
        when(dropbox.download("id:it-file")).thenReturn(new byte[] {9, 8, 7});
        when(dropbox.revision("id:it-file")).thenReturn(new FileRevision("id:it-file", "/notes.txt", "rev1"));

        var file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {9, 8, 7});
        var uploaded = mvc.perform(multipart("/attachments").file(file).param("taskId", "" + taskId)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(uploaded.getStatus()).isEqualTo(201);
        long attachmentId = mapper.readTree(uploaded.getContentAsString()).get("id").asLong();

        assertThat(json(200, "GET", "/attachments/metadata?taskId=" + taskId, owner.token(), null)
                .get("content").get(0).get("filename").asText()).isEqualTo("notes.txt");
        var zip = call("GET", "/attachments?taskId=" + taskId, owner.token(), null);
        assertThat(zip.getStatus()).isEqualTo(200);
        assertThat(zip.getContentType()).isEqualTo("application/zip");
        var download = call("GET", "/attachments/" + attachmentId + "/download", owner.token(), null);
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).containsExactly((byte) 9, (byte) 8, (byte) 7);
        assertThat(call("DELETE", "/attachments/" + attachmentId, owner.token(), null).getStatus())
                .isEqualTo(204);
        assertThat(call("GET", "/attachments/metadata?taskId=" + taskId, null, null).getStatus())
                .isEqualTo(401);
    }
}
