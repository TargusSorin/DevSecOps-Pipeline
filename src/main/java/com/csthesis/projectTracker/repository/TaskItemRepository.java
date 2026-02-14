package com.csthesis.projectTracker.repository;

import com.csthesis.projectTracker.model.TaskItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskItemRepository extends JpaRepository<TaskItem, Long> {

    List<TaskItem> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<TaskItem> findByIdAndProjectId(Long id, Long projectId);
}
