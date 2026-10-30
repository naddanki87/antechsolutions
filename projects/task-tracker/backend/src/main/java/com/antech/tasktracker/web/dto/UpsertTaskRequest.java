package com.antech.tasktracker.web.dto;

import java.time.LocalDate;

import com.antech.tasktracker.domain.Priority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** PUT body. {@code version} is required to update an existing task and ignored when creating. */
public record UpsertTaskRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String notes,
        LocalDate dueDate,
        Priority priority,
        Boolean done,
        @PositiveOrZero Long version) {
}
