# Task Tracker - Specification (Spec)

**Requirement (CEO Nageshwar):** Task tracker: users create tasks with a title, notes, due date and priority,
mark them done, and filter by status. The Android app works fully offline and syncs when online.

## Scope
- Single-tenant, no login in v1.0.0 (Armor-approved: no personal data beyond task text; auth is a v1.1 item).
- Web UI served by the backend; Android app is local-first with optional sync.

## Domain model - `Task`
| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Server-generated on POST; client-generated on PUT (offline create) |
| `title` | string | required, 1-120 chars after trim |
| `notes` | string? | max 1000 chars |
| `dueDate` | date? (`yyyy-MM-dd`) | optional |
| `priority` | enum `LOW`, `MEDIUM`, `HIGH` | default `MEDIUM` |
| `done` | boolean | default `false` |
| `deleted` | boolean | soft delete flag, only visible in `/changes` |
| `version` | long | optimistic locking; stale -> 409 |
| `createdAt`, `updatedAt` | ISO-8601 UTC instant | server-managed |

## User stories and acceptance criteria
- **US-1 Create task.** AC-1: valid POST returns 201, `Location` header and the task with `done=false`,
  `priority=MEDIUM` when omitted, `version=0`. AC-2: blank title or title > 120 chars returns 400 ProblemDetail
  with a `title` field error.
- **US-2 List and filter.** AC-3: GET `/tasks` returns a page sorted by `createdAt desc` by default, excluding
  deleted tasks. AC-4: `?done=true|false` filters by status. AC-5: `size` above 100 is clamped to 100.
- **US-3 View task.** AC-6: GET `/tasks/{id}` returns 200; unknown or deleted id returns 404.
- **US-4 Update / complete.** AC-7: PUT with the current `version` updates and returns 200 with `version+1`.
  AC-8: PUT with a stale version returns 409. AC-9: PUT on an unknown id creates it (201) - offline create.
  AC-10: PUT on a deleted task returns 409.
- **US-5 Delete.** AC-11: DELETE returns 204 and the task disappears from list/get; unknown id returns 404.
- **US-6 Sync.** AC-12: GET `/tasks/changes?since=<instant>` returns all tasks (incl. deleted) with
  `updatedAt > since`, oldest first.
- **US-7 Android offline.** AC-13: the app creates, edits, completes and deletes tasks with no network, storing
  them in internal storage only. AC-14: "Sync" pushes local changes and pulls server changes; network failure
  keeps local data and shows "Offline".
- **US-8 Web.** AC-15: the web page lists tasks with a status filter, creates tasks, toggles done and deletes,
  showing loading, empty and error states.

## Android local storage (agreed with Swift)
Table `tasks` in `task-tracker.db` (app-private databases dir) mirrors the model plus `dirty`. Settings
(API base URL, last pull time) in private SharedPreferences. No storage permissions, backups excluded.

## Contract
See [`openapi.yaml`](openapi.yaml).
