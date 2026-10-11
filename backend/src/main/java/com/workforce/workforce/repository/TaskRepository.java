package com.workforce.workforce.repository;

import com.workforce.workforce.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByAssignedToIdOrderByDueDateAsc(Long employeeId);
    List<Task> findAllByOrderByCreatedAtDesc();
}
