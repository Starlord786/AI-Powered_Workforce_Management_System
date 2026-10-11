package com.workforce.workforce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Getter
@Setter
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "work_date")
    private LocalDate workDate;

    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    private String method = "MANUAL";
    private String status = "PRESENT";
    private Double workHours;
}
