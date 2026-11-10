# Changelog

## Unreleased

### Changed

- Add per-controller MockMvc integration tests and Mockito unit tests for
  service implementations.
- Replace the raw SQL Liquibase script with YAML changelog change types in
  `001-course-schema.yaml` (still changeset `course-001`).
- Extract service interfaces (`UserService`, `ProjectService`, `TaskService`,
  `CommentService`, `LabelService`, `AttachmentService`, `DropboxService`,
  `EmailService`, `DeadlineReminderService`) with Spring `@Service`
  `*ServiceImpl` implementations; controllers depend on the interfaces.
- Replace `ResponseStatusException` with typed `ApiException` subclasses
  (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`,
  `PayloadTooLarge`, `BadGateway`) handled by `GlobalExceptionHandler` as
  Problem Details. See
  [ARCHITECTURE.md — Custom API exceptions](documentation/ARCHITECTURE.md#custom-api-exceptions).
- Set `server.servlet.context-path=/api` and keep servlet paths without an
  `/api` prefix in controllers, `Location` headers, security matchers and tests.
  The container adds `/api` only at the HTTP boundary (Swagger/health browser
  URLs). Tests clear context-path so MockMvc calls `/auth`, `/users`, `/tasks`.
- Use Lombok `@RequiredArgsConstructor` for Spring DI constructors on controllers
  and `*ServiceImpl` classes. JwtService and EmailServiceImpl keep handwritten
  constructors (timing hash init and `@Value` parameter).
  lombok-mapstruct-binding is configured for annotation processing with MapStruct.
  Rationale:
  [ARCHITECTURE.md — Lombok constructors](documentation/ARCHITECTURE.md#lombok-constructors--rule-and-exceptions).
- JPA entities use Lombok `@Getter`/`@Setter`/`@NoArgsConstructor` instead of
  handwritten accessors; `@Data` is avoided. `id`/`version` have no setters.

### Documentation

- Documented OpenAPI/Swagger controller annotations, including the two special
  cases: `@Parameter(hidden = true)` for injected `Jwt`, and
  `schema type=string, format=binary` for multipart upload and file/ZIP download.
  Details: [ARCHITECTURE.md — OpenAPI / Swagger special cases](documentation/ARCHITECTURE.md#openapi--swagger-special-cases).

### Fixed

- Clean up newly uploaded Dropbox files when the database transaction rolls back,
  including checked stream-close failures. Restore the original Dropbox revision
  when attachment deletion rolls back. Log failed compensation for manual recovery.
- Return actual Dropbox files as a paginated ZIP from GET /api/attachments?taskId=....
  Metadata is available at /api/attachments/metadata; individual downloads remain
  available. Archive pages are limited to 25 MiB of uncompressed content.
- Notify the current task assignee and project owner after meaningful edits.
  Exclude the actor and duplicate recipients; unchanged PUTs send no email.
  Reassignment sends one assignment email to the new assignee.

### Delivery

- Protected main requires a successful verify check, an up-to-date PR branch,
  one approval and resolved conversations. Rules include administrators; force
  pushes and branch deletion are disabled.
- Retained Java 21, PostgreSQL 17, MapStruct, UTC and the existing Liquibase schema.
- Regression verification on a1b8300: Java 21 clean verify passed 38 tests with no
  failures, errors or skips, Checkstyle passed, and line coverage was 94.69%.
  PostgreSQL and packaged/container startup/restart also passed in
  [CI](https://github.com/ursusandwolfcom/task-management-course/actions/runs/42239198040).

### Limits

- Dropbox and the database do not share an atomic transaction. Process crashes,
  unknown commit outcomes and failed compensation require manual reconciliation.
- Email is sent after commit without durable retries. Local Mailpit capture and
  automated provider substitutes do not establish real Dropbox/SMTP acceptance.
