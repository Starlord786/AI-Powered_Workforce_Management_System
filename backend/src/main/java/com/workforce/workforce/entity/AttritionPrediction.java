package com.workforce.workforce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "attrition_predictions")
@Getter
@Setter
public class AttritionPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Employee employee;

    private Double riskScore;       // 0 to 1
    private String riskLevel;       // LOW, MEDIUM, HIGH

    @Column(columnDefinition = "TEXT")
    private String topFactors;      // factors separated by ";"

    private String suggestedAction;
    private LocalDateTime predictedAt = LocalDateTime.now();
}
