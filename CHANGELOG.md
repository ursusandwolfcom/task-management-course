# Changelog

## Unreleased

### Changed

- Use Lombok `@RequiredArgsConstructor` for Spring DI constructors on controllers
  and services. JwtService and EmailService keep handwritten constructors
  (timing hash init and `@Value` parameter). lombok-mapstruct-binding is configured
  for annotation processing with MapStruct.

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
