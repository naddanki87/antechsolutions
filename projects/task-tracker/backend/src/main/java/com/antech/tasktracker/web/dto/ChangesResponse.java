package com.antech.tasktracker.web.dto;

import java.util.List;

public record ChangesResponse(List<TaskResponse> content) {
}
