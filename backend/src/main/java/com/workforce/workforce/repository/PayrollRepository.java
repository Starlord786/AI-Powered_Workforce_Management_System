package com.workforce.workforce.repository;

import com.workforce.workforce.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    Optional<Payroll> findByEmployeeIdAndMonthAndYear(Long employeeId, int month, int year);
    List<Payroll> findByYearAndMonthOrderByEmployeeFirstNameAsc(int year, int month);
    List<Payroll> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);
}
