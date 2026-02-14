package com.csthesis.projectTracker.controller;

import com.csthesis.projectTracker.dto.project.ProjectRequest;
import com.csthesis.projectTracker.dto.project.ProjectResponse;
import com.csthesis.projectTracker.model.Project;
import com.csthesis.projectTracker.model.UserAccount;
import com.csthesis.projectTracker.repository.ProjectRepository;
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
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserAccountRepository userAccountRepository;

    public ProjectController(ProjectRepository projectRepository, UserAccountRepository userAccountRepository) {
        this.projectRepository = projectRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public List<ProjectResponse> listProjects(Authentication authentication) {
        UserAccount currentUser = requireCurrentUser(authentication);
        return projectRepository.findAllByOwnerIdOrderByIdAsc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication
    ) {
        UserAccount currentUser = requireCurrentUser(authentication);

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(normalizeNullable(request.description()));
        project.setOwner(currentUser);

        Project saved = projectRepository.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId, Authentication authentication) {
        Project project = requireOwnedProject(projectId, authentication);
        return toResponse(project);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication
    ) {
        Project project = requireOwnedProject(projectId, authentication);
        project.setName(request.name().trim());
        project.setDescription(normalizeNullable(request.description()));
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId, Authentication authentication) {
        Project project = requireOwnedProject(projectId, authentication);
        projectRepository.delete(project);
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

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription());
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
