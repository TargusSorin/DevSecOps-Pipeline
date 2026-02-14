package com.csthesis.projectTracker.dto.project;

public record ProjectResponse(
        Long id,
        String name,
        String description
) {
}
