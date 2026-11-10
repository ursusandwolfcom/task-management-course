package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.LabelRequest;
import com.example.taskmanagement.dto.LabelResponse;
import com.example.taskmanagement.service.LabelService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Labels",
        description = "Shared task labels with unique names and #RRGGBB colors. JWT required.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/labels")
public class LabelController {
    private final LabelService labels;

    @GetMapping
    @Operation(summary = "List labels",
            description = "Returns a paginated label catalog. Supports page/size/sort.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of labels"),
            @ApiResponse(responseCode = "400", description = "Invalid sort field",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public Page<LabelResponse> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return labels.list(pageable);
    }

    @PostMapping
    @Operation(summary = "Create label",
            description = "Creates a label. Name is unique (max 100 chars); color uses #RRGGBB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Label created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LabelResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Label name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody LabelRequest request) {
        var result = labels.create(request);
        return ResponseEntity.created(URI.create("/api/labels")).body(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update label",
            description = "Replaces label name and color.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Label updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LabelResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Label not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Label name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public LabelResponse update(
            @Parameter(description = "Label ID", required = true) @PathVariable Long id,
            @Valid @RequestBody LabelRequest request) {
        return labels.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete label",
            description = "Deletes a label. Tasks referencing it must no longer use it, "
                    + "or the database may reject the delete with a conflict.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Label deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Label not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Label is still referenced",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Label ID", required = true) @PathVariable Long id) {
        labels.delete(id);
        return ResponseEntity.noContent().build();
    }
}
