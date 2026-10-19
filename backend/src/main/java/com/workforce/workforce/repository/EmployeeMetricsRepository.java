package com.workforce.workforce.repository;

import com.workforce.workforce.entity.EmployeeMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeMetricsRepository extends JpaRepository<EmployeeMetrics, Long> {
    Optional<EmployeeMetrics> findByEmployeeId(Long employeeId);
}
