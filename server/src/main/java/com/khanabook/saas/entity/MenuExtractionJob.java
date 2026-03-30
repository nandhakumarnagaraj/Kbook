package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "menu_extraction_jobs")
public class MenuExtractionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long restaurantId;

    // Path to where the uploaded PDF/Image is saved locally on the server
    private String filePath;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    // Store the extracted JSON result here once complete
    @Column(columnDefinition = "TEXT")
    private String extractedDataJson;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    // To handle errors if extraction fails
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
