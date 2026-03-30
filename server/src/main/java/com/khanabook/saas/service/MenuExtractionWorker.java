package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.entity.MenuExtractionJob;
import com.khanabook.saas.repository.MenuExtractionJobRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import technology.tabula.*;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuExtractionWorker {

    @Autowired
    private MenuExtractionJobRepository jobRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Async("menuExtractionExecutor")
    public void processMenuJob(Long jobId) {
        MenuExtractionJob job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        job.setStatus(MenuExtractionJob.JobStatus.PROCESSING);
        jobRepository.save(job);

        try {
            File file = new File(job.getFilePath());
            List<Map<String, String>> extractedItems = new ArrayList<>();

            // ATTEMPT TABULA (For Text-Based PDFs - 100% Accurate Variants)
            boolean tabulaSuccess = extractWithTabula(file, extractedItems);

            if (tabulaSuccess && !extractedItems.isEmpty()) {
                job.setExtractedDataJson(objectMapper.writeValueAsString(extractedItems));
                job.setStatus(MenuExtractionJob.JobStatus.COMPLETED);
            } else {
                // Future fallback for image-based PDFs here (e.g. PaddleOCR)
                job.setErrorMessage("No tables could be extracted from this PDF using offline parsing.");
                job.setStatus(MenuExtractionJob.JobStatus.FAILED);
            }
        } catch (Exception e) {
            job.setStatus(MenuExtractionJob.JobStatus.FAILED);
            job.setErrorMessage("Error: " + e.getMessage());
        } finally {
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    private boolean extractWithTabula(File pdfFile, List<Map<String, String>> resultList) {
        if (!pdfFile.getName().toLowerCase().endsWith(".pdf")) return false;

        boolean foundText = false;
        try (PDDocument document = PDDocument.load(pdfFile)) {
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                Page page = oe.extract(i);
                List<Table> tables = sea.extract(page);
                
                if (!tables.isEmpty()) foundText = true;

                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {
                        if (row.size() >= 3 && !row.get(0).getText().isEmpty()) {
                            Map<String, String> item = new HashMap<>();
                            String itemName = row.get(0).getText().replace("\r", " ").replace("\n", " ").trim();
                            
                            // Ignore header rows
                            if (itemName.equalsIgnoreCase("Item") || itemName.isEmpty()) continue;
                            
                            item.put("itemName", itemName);
                            item.put("halfPrice", row.get(1).getText().replace("\r", " ").replace("\n", " ").trim());
                            item.put("fullPrice", row.get(2).getText().replace("\r", " ").replace("\n", " ").trim());
                            
                            if (row.size() >= 4) {
                                item.put("description", row.get(3).getText().replace("\r", " ").replace("\n", " ").trim());
                            }
                            resultList.add(item);
                        } else if (row.size() == 2 && !row.get(0).getText().isEmpty()) {
                            // Might be a row with just item and price (no variants)
                            Map<String, String> item = new HashMap<>();
                            String itemName = row.get(0).getText().replace("\r", " ").replace("\n", " ").trim();
                            if (itemName.equalsIgnoreCase("Item") || itemName.isEmpty()) continue;
                            
                            item.put("itemName", itemName);
                            item.put("price", row.get(1).getText().replace("\r", " ").replace("\n", " ").trim());
                            resultList.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return foundText;
    }
}
