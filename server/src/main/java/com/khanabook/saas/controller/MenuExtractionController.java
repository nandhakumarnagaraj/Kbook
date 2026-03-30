package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MenuExtractionJob;
import com.khanabook.saas.repository.MenuExtractionJobRepository;
import com.khanabook.saas.service.MenuExtractionWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.khanabook.saas.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuExtractionController {

    @Autowired
    private MenuExtractionJobRepository jobRepository;

    @Autowired
    private MenuExtractionWorker menuExtractionWorker;

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMenu(@RequestParam("file") MultipartFile file) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            // 1. Create directory if not exists
            Path uploadPath = Paths.get(uploadDir).resolve("shop_" + restaurantId).normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 2. Save file to disk with safe name
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            
            // Validate extension for basic safety
            if (!extension.equalsIgnoreCase(".pdf") && !extension.equalsIgnoreCase(".jpg") && 
                !extension.equalsIgnoreCase(".jpeg") && !extension.equalsIgnoreCase(".png")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type"));
            }

            String fileName = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(fileName).normalize();
            
            // Extra check to ensure we are still inside the base upload directory
            if (!filePath.startsWith(Paths.get(uploadDir).normalize())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file path"));
            }

            file.transferTo(filePath.toFile());

            // 3. Create job in DB
            MenuExtractionJob job = new MenuExtractionJob();
            job.setRestaurantId(restaurantId);
            job.setFilePath(filePath.toAbsolutePath().toString());
            job = jobRepository.save(job);

            // 4. Trigger Async Processing
            menuExtractionWorker.processMenuJob(job.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Menu uploaded successfully. Processing in background.");
            response.put("jobId", job.getId());
            response.put("status", job.getStatus());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable Long jobId) {
        return jobRepository.findById(jobId)
                .map(job -> ResponseEntity.ok((Object) job))
                .orElse(ResponseEntity.notFound().build());
    }
}
