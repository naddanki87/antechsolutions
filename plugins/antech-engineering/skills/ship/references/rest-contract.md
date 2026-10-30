# REST contract conventions (Spec + Byte)

## Paths
- Base path `/api/v1`. Plural nouns: `/api/v1/leave-requests`, `/api/v1/leave-requests/{id}`.
- Actions that are not CRUD use a sub-resource verb: `POST /api/v1/leave-requests/{id}/approve`.
- IDs are UUIDs (string) so the Android app can create records offline and sync later without collisions.

## Methods and status codes
| Operation | Method | Success | Common errors |
|---|---|---|---|
| List (paged) | GET `/things?page=0&size=20&sort=createdAt,desc` | 200 | 400 |
| Get one | GET `/things/{id}` | 200 | 404 |
| Create | POST `/things` | 201 + `Location` header | 400, 409 |
| Upsert (sync) | PUT `/things/{id}` | 200 (updated) / 201 (created) | 400, 409 |
| Partial update | PATCH `/things/{id}` | 200 | 400, 404, 409 |
| Delete | DELETE `/things/{id}` | 204 | 404 |

Auth-enabled products add 401 (not authenticated) and 403 (not allowed) to every protected endpoint.

## Payloads
- JSON, camelCase, ISO-8601 UTC timestamps (`2026-10-30T10:15:30Z`), `LocalDate` as `yyyy-MM-dd`.
- Every resource has `id`, `createdAt`, `updatedAt`, `version` (optimistic locking via `@Version`; stale
  version -> 409).
- Separate request DTOs (`CreateThingRequest`, `UpdateThingRequest`) from response DTOs (`ThingResponse`).
  Never expose JPA entities directly.
- Page response:
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

## Sync contract (for the Android app)
- `GET /api/v1/things/changes?since=<ISO timestamp>` returns `{ "content": [ ... ] }` with every record whose
  `updatedAt > since`, including soft-deleted ones with `"deleted": true` (so the server soft-deletes too).
- Declare `/changes` before `/{id}` in the controller and keep `{id}` UUID-typed so the two never clash.
- `PUT /api/v1/things/{id}` is idempotent upsert; the client sends `version`; server returns 409 on conflict
  and the client keeps the server copy (last-writer-wins by server) unless Spec decides otherwise.

## Error model - RFC 7807 ProblemDetail (`application/problem+json`)
```json
{
  "type": "https://antech.dev/problems/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/things",
  "errors": [ { "field": "name", "message": "must not be blank" } ],
  "timestamp": "2026-10-30T10:15:30Z"
}
```
Types: `validation` (400), `malformed-request` (400), `not-found` (404), `conflict` (409),
`unauthorized` (401), `forbidden` (403), `internal` (500 - generic detail, never a stack trace).

## OpenAPI
`docs/openapi.yaml` (OpenAPI 3.1) is written by Spec first. After implementation, Hawk checks that
`/v3/api-docs` from the running app matches its paths and schemas; differences are bugs.
