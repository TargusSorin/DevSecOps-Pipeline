package com.csthesis.projectTracker.controller;

import com.csthesis.projectTracker.dto.task.TaskRequest;
import com.csthesis.projectTracker.dto.task.TaskResponse;
import com.csthesis.projectTracker.model.Project;
import com.csthesis.projectTracker.model.TaskItem;
import com.csthesis.projectTracker.model.TaskStatus;
import com.csthesis.projectTracker.model.UserAccount;
import com.csthesis.projectTracker.repository.ProjectRepository;
import com.csthesis.projectTracker.repository.TaskItemRepository;
import com.csthesis.projectTracker.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskItemRepository taskItemRepository;
    private final ProjectRepository projectRepository;
    private final UserAccountRepository userAccountRepository;

    public TaskController(
            TaskItemRepository taskItemRepository,
            ProjectRepository projectRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.taskItemRepository = taskItemRepository;
        this.projectRepository = projectRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public List<TaskResponse> listTasks(@PathVariable Long projectId, Authentication authentication) {
        Project project = requireOwnedProject(projectId, authentication);
        return taskItemRepository.findAllByProjectIdOrderByIdAsc(project.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable Long projectId,
            @Valid @RequestBody TaskRequest request,
            Authentication authentication
    ) {
        Project project = requireOwnedProject(projectId, authentication);

        TaskItem task = new TaskItem();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(normalizeNullable(request.description()));
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setDueDate(request.dueDate());

        TaskItem saved = taskItemRepository.save(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{taskId}")
    public TaskResponse updateTask(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskRequest request,
            Authentication authentication
    ) {
        Project project = requireOwnedProject(projectId, authentication);
        TaskItem task = taskItemRepository.findByIdAndProjectId(taskId, project.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        task.setTitle(request.title().trim());
        task.setDescription(normalizeNullable(request.description()));
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setDueDate(request.dueDate());

        TaskItem saved = taskItemRepository.save(task);
        return toResponse(saved);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        Project project = requireOwnedProject(projectId, authentication);
        TaskItem task = taskItemRepository.findByIdAndProjectId(taskId, project.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        taskItemRepository.delete(task);
        return ResponseEntity.noContent().build();
    }

    private Project requireOwnedProject(Long projectId, Authentication authentication) {
        UserAccount currentUser = requireCurrentUser(authentication);
        return projectRepository.findByIdAndOwnerId(projectId, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private UserAccount requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userAccountRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private TaskResponse toResponse(TaskItem task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getProject().getId()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
