package com.antech.tasktracker.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.antech.tasktracker.domain.Priority;
import com.antech.tasktracker.domain.Task;

public record TaskResponse(
        UUID id,
        String title,
        String notes,
        LocalDate dueDate,
        Priority priority,
        boolean done,
        boolean deleted,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getTitle(), t.getNotes(), t.getDueDate(), t.getPriority(), t.isDone(),
                t.isDeleted(), t.getVersion() == null ? 0 : t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
