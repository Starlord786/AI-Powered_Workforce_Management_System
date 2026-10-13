package com.workforce.workforce.repository;

import com.workforce.workforce.entity.FaceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FaceDataRepository extends JpaRepository<FaceData, Long> {
    List<FaceData> findByEmployeeId(Long employeeId);
    long countByEmployeeId(Long employeeId);

    @Query("select f.employee.id, count(f) from FaceData f group by f.employee.id")
    List<Object[]> countPerEmployee();
}
