# Task Tracker API reference (Scribe)

Base URL: `http://localhost:8080/api/v1`. JSON in and out. Errors are RFC 7807 `application/problem+json`:

```json
{ "type": "https://antech.dev/problems/validation", "title": "Validation failed", "status": 400,
  "detail": "One or more fields are invalid", "instance": "/api/v1/tasks",
  "errors": [ { "field": "title", "message": "must not be blank" } ], "timestamp": "2026-10-30T10:15:30Z" }
```
Problem types: `validation` (400), `malformed-request` (400), `request-error` (other 4xx), `not-found` (404),
`conflict` (409), `internal` (500, includes `correlationId`).

## Task object
```json
{
  "id": "3f2b8c1e-6a1d-4f0e-9a43-0f5c2b7d9e11",
  "title": "Pay invoices",
  "notes": "Supplier batch",
  "dueDate": "2026-11-05",
  "priority": "HIGH",
  "done": false,
  "deleted": false,
  "version": 0,
  "createdAt": "2026-10-30T10:15:30Z",
  "updatedAt": "2026-10-30T10:15:30Z"
}
```

## List tasks - `GET /tasks`
Query: `done` (true/false, optional), `page` (0..), `size` (1-100, larger values are clamped), `sort` (default `createdAt,desc`).
```bash
curl "http://localhost:8080/api/v1/tasks?done=false&size=20&sort=dueDate,asc"
```
`200` `{ "content": [Task...], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }` · `400` unknown sort field.

## Create a task - `POST /tasks`
Body: `title` (required, max 120), `notes` (max 1000), `dueDate` (`yyyy-MM-dd`), `priority` (`LOW|MEDIUM|HIGH`, default `MEDIUM`).
```bash
curl -i -X POST http://localhost:8080/api/v1/tasks -H "Content-Type: application/json" \
  -d '{"title":"Pay invoices","dueDate":"2026-11-05","priority":"HIGH"}'
```
`201` + `Location: /api/v1/tasks/{id}` + Task · `400` validation.

## Get a task - `GET /tasks/{id}`
```bash
curl http://localhost:8080/api/v1/tasks/3f2b8c1e-6a1d-4f0e-9a43-0f5c2b7d9e11
```
`200` Task · `404` unknown or deleted · `400` id is not a UUID.

## Update or create - `PUT /tasks/{id}`
Body: the create fields plus `done` and `version`. Send the `version` you last read; it is ignored when the id does not exist yet (offline create).
```bash
curl -X PUT http://localhost:8080/api/v1/tasks/3f2b8c1e-6a1d-4f0e-9a43-0f5c2b7d9e11 -H "Content-Type: application/json" \
  -d '{"title":"Pay invoices","priority":"HIGH","done":true,"version":0}'
```
`200` updated (version + 1) · `201` created · `409` stale version or task deleted · `400` validation.

## Delete - `DELETE /tasks/{id}`
```bash
curl -i -X DELETE http://localhost:8080/api/v1/tasks/3f2b8c1e-6a1d-4f0e-9a43-0f5c2b7d9e11
```
`204` · `404` unknown. Deletes are soft so sync clients can learn about them.

## Sync feed - `GET /tasks/changes?since=<instant>`
Returns up to 500 tasks (including deleted ones) changed after `since`, oldest first. Repeat with the last
`updatedAt` until fewer than 500 come back.
```bash
curl "http://localhost:8080/api/v1/tasks/changes?since=2026-10-30T00:00:00Z"
```
`200` `{ "content": [Task...] }` · `400` missing or invalid `since`.

## Operations
- `GET /actuator/health` -> `{"status":"UP"}`
- `GET /v3/api-docs` (OpenAPI JSON) and `/swagger-ui.html`
