package com.antech.tasktracker.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.antech.tasktracker.domain.Task;
import com.antech.tasktracker.service.TaskService;
import com.antech.tasktracker.web.dto.ChangesResponse;
import com.antech.tasktracker.web.dto.CreateTaskRequest;
import com.antech.tasktracker.web.dto.PageResponse;
import com.antech.tasktracker.web.dto.TaskResponse;
import com.antech.tasktracker.web.dto.UpsertTaskRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List tasks (excludes deleted), optionally filtered by done")
    public PageResponse<TaskResponse> list(@RequestParam(required = false) Boolean done,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.list(done, pageable), TaskResponse::from);
    }

    @PostMapping
    @Operation(summary = "Create a task")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.getId())).body(TaskResponse.from(task));
    }

    @GetMapping("/changes")
    @Operation(summary = "Tasks changed after an instant, including deleted ones (sync feed)")
    public ChangesResponse changes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return new ChangesResponse(service.changesSince(since).stream().map(TaskResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task")
    public TaskResponse get(@PathVariable UUID id) {
        return TaskResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task, or create it with this id (idempotent upsert)")
    public ResponseEntity<TaskResponse> upsert(@PathVariable UUID id, @Valid @RequestBody UpsertTaskRequest request) {
        TaskService.UpsertResult result = service.upsert(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(TaskResponse.from(result.task()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (soft delete)")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
