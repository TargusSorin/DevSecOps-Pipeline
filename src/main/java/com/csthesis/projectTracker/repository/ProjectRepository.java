package com.csthesis.projectTracker.repository;

import com.csthesis.projectTracker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
}
