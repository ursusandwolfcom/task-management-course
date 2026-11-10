package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.service.AttachmentService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Attachments",
        description = "Dropbox-backed task files: upload, metadata, ZIP archive download, "
                + "single-file download and delete. Size limit 1 B to 5 MB.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentService attachments;

    @GetMapping("/metadata")
    @Operation(summary = "List attachment metadata",
            description = "Paginated metadata for a task without downloading file bytes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of attachment metadata"),
            @ApiResponse(responseCode = "400", description = "Invalid sort field",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public Page<AttachmentResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @RequestParam Long taskId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return attachments.list(Long.valueOf(jwt.getSubject()), taskId, pageable);
    }

    @GetMapping(produces = "application/zip")
    @Operation(summary = "Download attachments as ZIP",
            description = "Returns a ZIP page of Dropbox files for the task. Entries use "
                    + "<attachmentId>/<originalFilename>. Empty page returns an empty ZIP. "
                    + "Uncompressed page limit is 25 MiB.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ZIP archive",
                    content = @Content(mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "Archive page exceeds 25 MiB",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Dropbox/file transfer failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<byte[]> archive(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @RequestParam Long taskId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable)
            throws IOException {
        var result = attachments.archive(Long.valueOf(jwt.getSubject()), taskId, pageable);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.filename(), StandardCharsets.UTF_8).build().toString())
                .body(result.content());
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download one attachment",
            description = "Streams a single Dropbox file as an octet-stream download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File bytes",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Attachment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Dropbox/file transfer failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<byte[]> download(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Attachment ID", required = true) @PathVariable Long id)
            throws IOException {
        var result = attachments.download(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.filename(), StandardCharsets.UTF_8).build().toString())
                .body(result.content());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an attachment",
            description = "Multipart upload to Dropbox for a task. Filename must be a single path "
                    + "segment without control characters. Size: 1 B to 5 MB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Attachment stored",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AttachmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filename or file size",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Dropbox operation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AttachmentResponse> upload(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @RequestParam Long taskId,
            @Parameter(description = "File to upload (1 B to 5 MB)", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam MultipartFile file) throws IOException {
        var result = attachments.upload(Long.valueOf(jwt.getSubject()), taskId, file);
        return ResponseEntity.created(URI.create("/attachments/" + result.id() + "/download")).body(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an attachment",
            description = "Deletes Dropbox content and local metadata. Project participants may delete.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Attachment deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Attachment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Dropbox operation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Attachment ID", required = true) @PathVariable Long id) {
        attachments.delete(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
