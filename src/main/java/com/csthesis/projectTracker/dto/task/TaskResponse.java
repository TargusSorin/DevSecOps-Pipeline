package com.csthesis.projectTracker.dto.task;

import com.csthesis.projectTracker.model.TaskStatus;

import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Long projectId
) {
}
