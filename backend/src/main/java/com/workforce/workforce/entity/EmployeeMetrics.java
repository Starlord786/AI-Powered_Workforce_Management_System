package com.workforce.workforce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** HR data the attrition model needs. One row per employee. */
@Entity
@Table(name = "employee_metrics")
@Getter
@Setter
public class EmployeeMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "employee_id", unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Employee employee;

    private Integer age;
    private Integer distanceFromHome;          // km
    private Integer jobLevel;                  // 1-5
    private Integer jobSatisfaction;           // 1-4
    private Integer workLifeBalance;           // 1-4
    private Integer environmentSatisfaction;   // 1-4
    private Boolean overtime;
    private Integer yearsAtCompany;
    private Integer yearsSinceLastPromotion;
    private Integer totalWorkingYears;
    private Integer numCompaniesWorked;

    /** true = made up automatically for the demo, not entered by HR */
    private Boolean demoData = false;
}
