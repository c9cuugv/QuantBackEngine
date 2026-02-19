package com.quantbackengine.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * Controller for uploading and managing stock data files.
 */
@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FileUploadController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Upload a CSV file with stock data.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("symbol") String symbol) {

        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("error", "File is empty");
            return ResponseEntity.badRequest().body(response);
        }

        if (symbol == null || symbol.trim().isEmpty()) {
            response.put("success", false);
            response.put("error", "Symbol is required");
            return ResponseEntity.badRequest().body(response);
        }

        // Content type check
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("text/csv") && !contentType.equals("application/vnd.ms-excel")) {
            // Some browsers send Excel for CSV, so we allow it, but we should strictly
            // check extension as we do below.
            // Just logging or stricter check if needed. Keeping extension check as primary
            // helper.
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            response.put("success", false);
            response.put("error", "Only CSV files are allowed");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Save file with symbol name
            String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9]", "");
            Path filePath = uploadPath.resolve(sanitizedSymbol + ".csv");
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Uploaded stock data for symbol: {} to {}", sanitizedSymbol, filePath);

            long rowCount = countCsvRows(filePath);

            // Validate that the file actually has content (header + data)
            if (rowCount <= 0) {
                Files.delete(filePath); // Cleanup empty/invalid file
                response.put("success", false);
                response.put("error", "CSV file is empty or contains only header");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("success", true);
            response.put("symbol", sanitizedSymbol);
            response.put("message", "File uploaded successfully");
            response.put("rows", rowCount);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get list of all available symbols (both predefined and uploaded).
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<Map<String, Object>>> getAvailableSymbols() {
        List<Map<String, Object>> symbols = new ArrayList<>();

        // Add predefined symbols from resources
        addSymbol(symbols, "AAPL", "Apple Inc", "predefined");

        // Add uploaded symbols
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (Files.exists(uploadPath)) {
                try (Stream<Path> paths = Files.list(uploadPath)) {
                    paths.filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                            .forEach(p -> {
                                String filename = p.getFileName().toString();
                                String symbol = filename.replace(".csv", "").toUpperCase();
                                addSymbol(symbols, symbol, symbol + " (Uploaded)", "uploaded");
                            });
                }
            }
        } catch (IOException e) {
            log.error("Error listing uploaded files: {}", e.getMessage());
        }

        return ResponseEntity.ok(symbols);
    }

    /**
     * Delete uploaded data for a symbol.
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> deleteSymbol(@PathVariable String symbol) {
        Map<String, Object> response = new HashMap<>();
        String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9]", "");

        try {
            Path filePath = Paths.get(uploadDir, sanitizedSymbol + ".csv");
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted stock data for symbol: {}", sanitizedSymbol);
                response.put("success", true);
                response.put("message", "Data deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Symbol not found or is predefined");
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to delete file");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private void addSymbol(List<Map<String, Object>> list, String symbol, String name, String type) {
        // Check if symbol already exists
        boolean exists = list.stream().anyMatch(m -> symbol.equals(m.get("symbol")));
        if (!exists) {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("name", name);
            map.put("type", type);
            list.add(map);
        }
    }

    private long countCsvRows(Path filePath) {
        try (Stream<String> stream = Files.lines(filePath)) {
            return stream.count() - 1; // Subtract header row
        } catch (IOException e) {
            log.warn("Failed to count lines in file: {}", filePath, e);
            return 0;
        }
    }
}
