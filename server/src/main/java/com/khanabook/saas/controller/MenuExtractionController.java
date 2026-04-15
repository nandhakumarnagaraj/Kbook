package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MenuExtractionJob;
import com.khanabook.saas.repository.MenuExtractionJobRepository;
import com.khanabook.saas.service.MenuExtractionWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
@RequestMapping("/menus")
@Tag(name = "menu-extraction-controller", description = "Upload menu files and track extraction jobs")
@SecurityRequirement(name = "Bearer Authentication")
public class MenuExtractionController {

    @Autowired
    private MenuExtractionJobRepository jobRepository;

    @Autowired
    private MenuExtractionWorker menuExtractionWorker;

    @Value("${storage.upload-dir}")
    private String uploadDir;

    private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024; // 10 MB

    @Operation(
            summary = "Upload a menu file for extraction",
            description = "Accepts a PDF or image upload, stores it for the authenticated tenant, and starts async extraction."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Menu accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid upload payload", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "413", description = "File too large", content = @Content),
            @ApiResponse(responseCode = "500", description = "Upload failed", content = @Content)
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadMenu(@RequestParam("file") MultipartFile file) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.status(413).body(Map.of("error", "File exceeds maximum allowed size of 10 MB"));
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

    @Operation(
            summary = "Get menu extraction job status",
            description = "Returns the current status and extracted data for a previously uploaded menu extraction job."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Job not found", content = @Content)
    })
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@Parameter(description = "Menu extraction job ID") @PathVariable Long jobId) {
        return jobRepository.findById(jobId)
                .map(job -> ResponseEntity.ok((Object) job))
                .orElse(ResponseEntity.notFound().build());
    }
}
