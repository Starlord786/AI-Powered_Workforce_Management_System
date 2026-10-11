package com.workforce.workforce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "pay_month", "pay_year"}))
@Getter
@Setter
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "pay_month")
    private int month;

    @Column(name = "pay_year")
    private int year;

    private BigDecimal baseSalary;
    private int workingDays;
    private int presentDays;
    private int leaveDays;
    private int absentDays;
    private int lateCount;

    private BigDecimal bonus = BigDecimal.ZERO;
    private BigDecimal absenceDeduction = BigDecimal.ZERO;
    private BigDecimal lateDeduction = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal netSalary = BigDecimal.ZERO;

    private LocalDateTime generatedAt;
    private String generatedBy;
}
