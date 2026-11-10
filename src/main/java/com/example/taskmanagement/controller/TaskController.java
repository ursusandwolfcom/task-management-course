package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.TaskFilter;
import com.example.taskmanagement.dto.TaskRequest;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.service.TaskService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tasks",
        description = "Task CRUD, assignment, labels and filtered search within a project. "
                + "Meaningful updates may email the assignee and project owner after commit.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    @GetMapping
    @Operation(summary = "List tasks in a project",
            description = "Requires projectId. Optional filters: name, status, priority, assigneeId, "
                    + "labelId, dueFrom, dueTo. Supports page/size/sort.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tasks"),
            @ApiResponse(responseCode = "400", description = "Invalid filter, date range or sort",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public Page<TaskResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Project ID", required = true) @RequestParam Long projectId,
            @Valid @ModelAttribute TaskFilter filter,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(Long.valueOf(jwt.getSubject()), projectId, filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID",
            description = "Returns a task if the caller can access its project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
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
    public TaskResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @PathVariable Long id) {
        return service.get(Long.valueOf(jwt.getSubject()), id);
    }

    @PostMapping
    @Operation(summary = "Create task",
            description = "Creates a task in a project. Assignee must be a project participant. "
                    + "labelIds is required and may be empty. New assignees receive email after commit.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Project or label not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Assignee must belong to project",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TaskResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskRequest request) {
        var result = service.create(Long.valueOf(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/tasks/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task",
            description = "Full replace of editable fields. projectId cannot change. "
                    + "Status is manual metadata (for example COMPLETED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or projectId changed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not a project participant",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task or label not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Assignee must belong to project",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TaskResponse update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @PathVariable Long id,
            @Valid @RequestBody TaskRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete task",
            description = "Owner/ADMIN deletes a task. Dropbox attachments must be deleted first.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Project owner required",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Delete Dropbox attachments first",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Task ID", required = true) @PathVariable Long id) {
        service.delete(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
