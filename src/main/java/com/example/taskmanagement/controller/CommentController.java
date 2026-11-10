package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.CommentRequest;
import com.example.taskmanagement.dto.CommentResponse;
import com.example.taskmanagement.service.CommentService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Comments",
        description = "Task discussion comments. Author and UTC timestamp are set by the server. "
                + "New comments may email the assignee after commit.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService comments;

    @GetMapping
    @Operation(summary = "List comments for a task",
            description = "Requires taskId. Returns a paginated page ordered by id descending by default.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of comments"),
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
    public Page<CommentResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @RequestParam Long taskId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return comments.list(Long.valueOf(jwt.getSubject()), taskId, pageable);
    }

    @PostMapping
    @Operation(summary = "Add a comment",
            description = "Creates a comment on a task. Text is required (max 4000 characters).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
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
    public ResponseEntity<CommentResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommentRequest request) {
        var result = comments.create(Long.valueOf(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/comments?taskId=" + result.taskId())).body(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a comment",
            description = "Allowed for the comment author, project owner or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Comment author or project owner required",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Comment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Comment ID", required = true) @PathVariable Long id) {
        comments.delete(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
