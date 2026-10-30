package com.antech.tasktracker.web.dto;

import java.time.LocalDate;

import com.antech.tasktracker.domain.Priority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String notes,
        LocalDate dueDate,
        Priority priority) {
}
