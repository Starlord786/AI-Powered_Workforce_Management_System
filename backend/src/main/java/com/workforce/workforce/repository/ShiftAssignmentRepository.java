package com.workforce.workforce.repository;

import com.workforce.workforce.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findByWorkDateBetween(LocalDate from, LocalDate to);

    List<ShiftAssignment> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ShiftAssignment a where a.workDate between :startDate and :endDate")
    void deleteInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
