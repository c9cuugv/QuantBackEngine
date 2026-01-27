package com.quantbackengine.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for loading market data.
 * Loads from CSV files in resources or from uploads folder.
 */
@Service
@Slf4j
public class MarketDataService {

    private static final ZoneId ZONE_ID = ZoneId.of("UTC");

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Load historical bar data for a symbol.
     * First checks uploads folder, then falls back to resources.
     */
    public BarSeries getBarSeries(String symbol, LocalDate start, LocalDate end) {
        String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9]", "");

        // First, try to load from uploads folder
        Path uploadPath = Paths.get(uploadDir, sanitizedSymbol + ".csv");
        if (Files.exists(uploadPath)) {
            log.info("Loading {} from uploads folder", sanitizedSymbol);
            return loadFromFile(uploadPath, sanitizedSymbol, start, end);
        }

        // Fall back to resources folder
        String resourcePath = "data/" + sanitizedSymbol + ".csv";
        return loadFromResource(resourcePath, sanitizedSymbol, start, end);
    }

    /**
     * Load from file system (uploads folder).
     */
    private BarSeries loadFromFile(Path filePath, String symbol, LocalDate start, LocalDate end) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile(), StandardCharsets.UTF_8))) {
            return parseCSV(reader, symbol, start, end);
        } catch (Exception e) {
            log.error("Error loading market data from file for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }
    }

    /**
     * Load from classpath resources.
     */
    private BarSeries loadFromResource(String resourcePath, String symbol, LocalDate start, LocalDate end) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("No CSV file found for symbol: {}", symbol);
                return new BaseBarSeries(symbol);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return parseCSV(reader, symbol, start, end);
            }
        } catch (Exception e) {
            log.error("Error loading market data for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }
    }

    /**
     * Parse CSV data into BarSeries.
     */
    private BarSeries parseCSV(BufferedReader reader, String symbol, LocalDate start, LocalDate end) throws Exception {
        // Skip preamble until header (handles MacroTrends format)
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().toLowerCase().startsWith("date")) {
                break;
            }
        }

        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .builder()
                .setHeader("Date", "Open", "High", "Low", "Close", "Volume")
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(reader);

        List<Bar> bars = new ArrayList<>();

        for (CSVRecord record : records) {
            try {
                LocalDate date = LocalDate.parse(record.get("Date"));
                if (date.isBefore(start) || date.isAfter(end)) {
                    continue;
                }

                Bar bar = new BaseBar(
                        Duration.ofDays(1),
                        date.atStartOfDay(ZONE_ID),
                        record.get("Open"),
                        record.get("High"),
                        record.get("Low"),
                        record.get("Close"),
                        record.get("Volume"));
                bars.add(bar);
            } catch (Exception e) {
                log.debug("Skipping malformed record: {}", e.getMessage());
            }
        }

        // Sort chronologically
        bars.sort((b1, b2) -> b1.getEndTime().compareTo(b2.getEndTime()));

        log.info("Loaded {} bars for {} from {} to {}", bars.size(), symbol, start, end);
        return new BaseBarSeries(symbol, bars);
    }

    /**
     * Get list of available symbols (predefined + uploaded).
     */
    public List<String> getAvailableSymbols() {
        List<String> symbols = new ArrayList<>();
        symbols.add("AAPL"); // Predefined

        // Add uploaded symbols
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (Files.exists(uploadPath)) {
                try (Stream<Path> paths = Files.list(uploadPath)) {
                    paths.filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                            .forEach(p -> {
                                String filename = p.getFileName().toString();
                                String symbol = filename.replace(".csv", "").toUpperCase();
                                if (!symbols.contains(symbol)) {
                                    symbols.add(symbol);
                                }
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error listing uploaded files: {}", e.getMessage());
        }

        return symbols;
    }
}
