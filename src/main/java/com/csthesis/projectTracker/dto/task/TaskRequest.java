package com.csthesis.projectTracker.dto.task;

import com.csthesis.projectTracker.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        TaskStatus status,
        LocalDate dueDate
) {
}
