package com.workforce.workforce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One stored face encoding (128 numbers as a JSON array string). Photos are NOT stored. */
@Entity
@Table(name = "face_data")
@Getter
@Setter
public class FaceData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encoding;

    private LocalDateTime createdAt = LocalDateTime.now();
}
