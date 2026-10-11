package com.workforce.workforce.repository;

import com.workforce.workforce.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    List<Attendance> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);
    List<Attendance> findAllByOrderByWorkDateDescCheckInDesc();
}
