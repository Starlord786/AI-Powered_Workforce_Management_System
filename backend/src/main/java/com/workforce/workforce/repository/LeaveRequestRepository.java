package com.workforce.workforce.repository;

import com.workforce.workforce.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, String status);
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
}
