package com.workforce.workforce.repository;

import com.workforce.workforce.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findAllByOrderByStartTimeAsc();
}
