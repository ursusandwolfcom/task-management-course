# API reference

[README](../README.md) · [Architecture](ARCHITECTURE.md) · [Operations](OPERATIONS.md)

Default Compose base URL: `http://127.0.0.1:8081/api`
(`server.servlet.context-path=/api`). All route tables below list servlet paths
without that prefix (for example `/tasks`, `/users/me`). Controllers and
`Location` headers use the same paths; the context path is applied by the
container. Browse `{base}/swagger-ui/index.html` or `{base}/v3/api-docs`.

## Authentication

Registration and login are public. All business endpoints require
`Authorization: Bearer <accessToken>`. Access tokens expire after 900 seconds;
log in again to obtain a new token. Authentication uses headers; there are no
browser-session cookies or refresh-token endpoints.

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create a USER account; returns 201 |
| POST | `/auth/login` | Authenticate by username or email; returns a token |
| GET | `/users/me` | Read the authenticated profile |
| PUT | `/users/me` | Replace email, first name and last name |
| PATCH | `/users/me` | Update supplied profile fields; omitted/null fields are retained |
| PUT | `/users/{id}/role` | Change a role; ADMIN only |

Registration requires username, password, email, firstName and lastName. Usernames
contain 3–64 letters, digits, underscores, periods or hyphens. Passwords contain at
least 8 characters and at most 72 UTF-8 bytes. Username/email comparisons ignore
case. Password hashes are never returned. Unknown JSON properties are rejected,
including a client-supplied registration role.

Login body: `{"identifier":"alice","password":"choose-a-strong-password"}`.
Token response:

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900 }
```

Profile PUT example:

```json
{ "email": "alice@example.com", "firstName": "Alice", "lastName": "Smith" }
```

Role body: `{"role":"ADMIN"}` or `{"role":"USER"}`. Roles are loaded on every
authenticated request, so a role change applies to already-issued JWTs.

## Routes

| Area | Method and path | Notes |
|---|---|---|
| Projects | `POST /projects` | Create; authenticated user becomes owner |
| Projects | `GET /projects` | List accessible projects; ADMIN can list all |
| Projects | `GET/PUT/DELETE /projects/{id}` | Read, replace or delete |
| Tasks | `POST /tasks` | Create within `projectId` |
| Tasks | `GET /tasks?projectId={id}` | Paginated project task list |
| Tasks | `GET/PUT/DELETE /tasks/{id}` | Read, replace or delete |
| Comments | `POST /comments` | Body contains taskId and text |
| Comments | `GET /comments?taskId={id}` | Paginated task discussion |
| Comments | `DELETE /comments/{id}` | Delete an authorized comment |
| Labels | `POST/GET /labels` | Create/list the shared catalog |
| Labels | `PUT/DELETE /labels/{id}` | Replace/delete a label |
| Attachments | `POST /attachments?taskId={id}` | Multipart form field `file` |
| Attachments | `GET /attachments?taskId={id}` | ZIP with actual Dropbox files for the requested page |
| Attachments | `GET /attachments/metadata?taskId={id}` | Paginated metadata list |
| Attachments | `GET /attachments/{id}/download` | Actual Dropbox bytes with Content-Disposition |
| Attachments | `DELETE /attachments/{id}` | Remove remote file and metadata |

Create responses use 201. Successful DELETE returns 204 without a body.
All six entity response types are produced by MapStruct.

## Walkthrough

Register/login as described in the README, authorize Swagger and note the ID
returned by `GET /users/me`. IDs below are examples; use your returned IDs.

**1. Create a project** — `POST /projects`:

```json
{
  "name": "Course API",
  "description": "Implement and review the REST API",
  "startDate": "2030-01-01",
  "endDate": "2030-12-31",
  "status": "INITIATED",
  "memberIds": []
}
```

To invite an existing registered user, include their ID in `memberIds` on project
PUT. Start/end dates are optional; when both are present, end must not precede start.

**2. Create a label** — `POST /labels`:

```json
{ "name": "backend", "color": "#4169E1" }
```

Names are unique, at most 100 characters; colors use `#RRGGBB`.

**3. Create and assign a task** — `POST /tasks`:

```json
{
  "name": "Implement task endpoints",
  "description": "Add CRUD and authorization checks",
  "priority": "HIGH",
  "status": "NOT_STARTED",
  "dueDate": "2030-02-11",
  "projectId": 1,
  "assigneeId": 1,
  "labelIds": [1]
}
```

Project owner or member may be the assignee. Omit/set null assigneeId for an
unassigned task. Description and dueDate are optional. `labelIds` is required and
may be empty. Project ID cannot change after creation. PUT replaces editable fields.
Assignment to a different non-null assignee produces an email after commit.
On task update, changes to name, description, status, priority, due date, labels
or assignee notify the current assignee and project owner after commit. The actor
is excluded and a user who is both owner and assignee receives one message.
A new assignee receives an assignment email instead of an additional update email.
A PUT with unchanged values sends no email; the previous assignee is not notified.

**4. Add a comment** — `POST /comments`:

```json
{ "taskId": 1, "text": "Ready for review." }
```

The server derives author and UTC timestamp. A comment emails the assignee unless
the assignee authored it. Text is required and limited to 4,000 characters.

**5. Upload a file** — use the multipart form in Swagger (binary part; see
[OpenAPI special cases](ARCHITECTURE.md#openapi--swagger-special-cases)), or run:

```powershell
curl.exe -X POST "http://127.0.0.1:8081/attachments?taskId=1" `
  -H "Authorization: Bearer YOUR_TOKEN" -F "file=@notes.txt"
```

Uploads accept 1 byte to 5 MB. The original filename must be a single name without
path separators or control characters. The response contains the stored Dropbox
file ID. `GET /attachments?taskId=1` downloads a ZIP containing actual Dropbox
files for the requested page. Entries use `<attachmentId>/<originalFilename>` so
duplicate filenames remain distinct. Pagination and sorting use the same parameters
as other lists; an empty page returns an empty ZIP. A page is limited to 25 MiB of
uncompressed content; use a smaller `size` if the server returns 413. Provider
failure returns 502 before a partial archive is sent.

Use `GET /attachments/metadata?taskId=1` for metadata and pagination totals,
or `GET /attachments/{id}/download` to download an individual file.

**6. Complete the task** — PUT the complete task body with `status` changed to
`COMPLETED`. Status fields are manually editable metadata; there are no execution
commands or attempt-history endpoints in this version.

## Statuses and filters

| Field | Accepted values |
|---|---|
| Project status | INITIATED, IN_PROGRESS, COMPLETED |
| Task status | NOT_STARTED, IN_PROGRESS, COMPLETED |
| Task priority | LOW, MEDIUM, HIGH |
| User role | USER, ADMIN |

Lists accept `page` (zero-based), `size` (default 20, maximum 100) and Spring Data
`sort`, for example `sort=name,asc`. Default order is ID descending. Unknown sort
properties are rejected with 400.

| List | Optional query filters |
|---|---|
| `/projects` | name, status, startFrom, endTo |
| `/tasks?projectId=...` | name, status, priority, assigneeId, labelId, dueFrom, dueTo |

Filters combine with AND. Name matching is a case-insensitive literal substring;
percent/underscore are not search wildcards. Dates use `YYYY-MM-DD`. Project bounds
select startDate >= startFrom and endDate <= endTo; task due bounds are inclusive.
A reversed range is invalid; null dates do not match a supplied date bound.

Example: `/tasks?projectId=1&priority=HIGH&status=NOT_STARTED&dueTo=2030-02-11`.

## Authorization and deletion

- Owner/ADMIN can update/delete projects and manage project membership.
- Project participants can read project content, create/update tasks and add
  comments/files. Outsiders cannot access project resources.
- Only owner/ADMIN deletes tasks. A comment author, project owner or ADMIN may
  delete a comment. Participants may delete task attachments.
- Reassign/unassign a member's tasks before removing them from the project.
- Delete Dropbox attachments before deleting their task. Task deletion removes
  comments and label links. Delete tasks before deleting their project.
- Labels form a shared catalog editable by authenticated users. Deleting one
  detaches its task associations.

## Error handling

Business failures throw typed `ApiException` subclasses from services. They are
not caught in controllers: Spring MVC routes them to
`@RestControllerAdvice` /
[GlobalExceptionHandler](../src/main/java/com/example/taskmanagement/exception/GlobalExceptionHandler.java),
which returns RFC 7807 `ProblemDetail` (`application/problem+json`). One
`@ExceptionHandler(ApiException.class)` covers every subclass via inheritance.
How interception works is explained under
[How GlobalExceptionHandler intercepts exceptions](ARCHITECTURE.md#how-globalexceptionhandler-intercepts-exceptions).
Class → status mapping:
[exception table](ARCHITECTURE.md#custom-api-exceptions).

| Status | Typical reason | Typed exception (when from services) |
|---|---|---|
| 400 | Validation, malformed dates/enums, unknown fields/sort, changed task project | `BadRequestException` (+ Bean Validation / sort handler) |
| 401 | Missing/invalid/expired token or invalid login credentials | `UnauthorizedException` (login); filter for missing JWT |
| 403 | Authenticated caller lacks owner/admin/comment-author permission | `ForbiddenException` |
| 404 | Missing resource or project content inaccessible to the caller | `NotFoundException` |
| 409 | Duplicate data, concurrent change, invalid assignee or deletion/member-removal guard | `ConflictException` (+ JPA/data-integrity handler) |
| 413 | Multipart request exceeds upload limits, or archive page exceeds 25 MiB | `PayloadTooLargeException` (archive); servlet limits for multipart |
| 502 | Dropbox operation or file transfer failed | `BadGatewayException` (+ `IOException` handler) |

Example problem body shape:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Task not found"
}
```

Authentication may be rejected by the security filter before MVC handles the
request. Public database health is `/actuator/health`; it does not test live
Dropbox or SMTP.
