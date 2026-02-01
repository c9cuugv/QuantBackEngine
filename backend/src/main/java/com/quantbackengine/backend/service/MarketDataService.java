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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Service for loading market data.
 * Loads from CSV files in resources or from uploads folder.
 */
@Service
@Slf4j
public class MarketDataService {

    private static final ZoneId ZONE_ID = ZoneId.of("UTC");

    // Cache available symbols to avoid frequent disk I/O
    private List<String> cachedSymbols;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION_MS = 10000; // 10 seconds

    // Cache for parsed BarSeries
    private final java.util.Map<String, CachedMarketData> barSeriesCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedMarketData(BarSeries series, long lastModified) {}

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Load historical bar data for a symbol.
     * First checks uploads folder, then falls back to resources.
     */
    public BarSeries getBarSeries(String symbol, LocalDate start, LocalDate end) {
        String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9]", "");
        BarSeries fullSeries;

        try {
            // First, try to load from uploads folder
            Path uploadPath = Paths.get(uploadDir, sanitizedSymbol + ".csv");
            if (Files.exists(uploadPath)) {
                long lastModified = Files.getLastModifiedTime(uploadPath).toMillis();
                CachedMarketData cached = barSeriesCache.get(sanitizedSymbol);

                if (cached != null && cached.lastModified() == lastModified) {
                    log.debug("Cache hit for {}", sanitizedSymbol);
                    fullSeries = cached.series();
                } else {
                    log.info("Loading {} from uploads folder", sanitizedSymbol);
                    fullSeries = loadFromFile(uploadPath, sanitizedSymbol);
                    barSeriesCache.put(sanitizedSymbol, new CachedMarketData(fullSeries, lastModified));
                }
            } else {
                // Fall back to resources folder
                // For resources, we assume they are static (lastModified = 0)
                CachedMarketData cached = barSeriesCache.get(sanitizedSymbol);
                if (cached != null && cached.lastModified() == 0) {
                    fullSeries = cached.series();
                } else {
                    String resourcePath = "data/" + sanitizedSymbol + ".csv";
                    fullSeries = loadFromResource(resourcePath, sanitizedSymbol);
                    // Only cache if we successfully loaded something (not empty)
                    if (!fullSeries.isEmpty()) {
                        barSeriesCache.put(sanitizedSymbol, new CachedMarketData(fullSeries, 0));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving bar series for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }

        // Return filtered view
        return filterSeries(fullSeries, start, end);
    }

    /**
     * Filter a BarSeries by date range.
     */
    private BarSeries filterSeries(BarSeries fullSeries, LocalDate start, LocalDate end) {
        if (fullSeries.isEmpty()) {
            return fullSeries;
        }

        List<Bar> filteredBars = new ArrayList<>();
        // Optimization: fullSeries is sorted. We can find range or just iterate.
        // Simple iteration is fast enough for memory-resident data.
        for (int i = 0; i < fullSeries.getBarCount(); i++) {
            Bar bar = fullSeries.getBar(i);
            LocalDate date = bar.getEndTime().toLocalDate();
            if (!date.isBefore(start) && !date.isAfter(end)) {
                filteredBars.add(bar);
            }
        }

        return new BaseBarSeries(fullSeries.getName(), filteredBars);
    }

    /**
     * Load from file system (uploads folder).
     */
    private BarSeries loadFromFile(Path filePath, String symbol) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile(), StandardCharsets.UTF_8))) {
            return parseCSV(reader, symbol);
        } catch (Exception e) {
            log.error("Error loading market data from file for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }
    }

    /**
     * Load from classpath resources.
     */
    private BarSeries loadFromResource(String resourcePath, String symbol) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("No CSV file found for symbol: {}", symbol);
                return new BaseBarSeries(symbol);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return parseCSV(reader, symbol);
            }
        } catch (Exception e) {
            log.error("Error loading market data for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }
    }

    /**
     * Parse CSV data into BarSeries.
     */
    private BarSeries parseCSV(BufferedReader reader, String symbol) throws Exception {
        // Skip preamble until header (handles MacroTrends format)
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().toLowerCase().startsWith("date")) {
                break;
            }
        }

        if (line == null) {
            log.warn("No header found starting with 'Date' for {}", symbol);
            return new BaseBarSeries(symbol);
        }

        // Parse the header line dynamically
        String[] headers = CSVFormat.DEFAULT.parse(new java.io.StringReader(line))
                .getRecords().get(0).toList().toArray(new String[0]);

        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .builder()
                .setHeader(headers)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(reader);

        List<Bar> bars = new ArrayList<>();

        for (CSVRecord record : records) {
            try {
                LocalDate date = LocalDate.parse(record.get("Date"));

                // Load ALL data
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

        log.info("Loaded full series: {} bars for {}", bars.size(), symbol);
        return new BaseBarSeries(symbol, bars);
    }

    /**
     * Get list of available symbols (predefined + uploaded).
     */
    public synchronized List<String> getAvailableSymbols() {
        long now = System.currentTimeMillis();
        if (cachedSymbols != null && (now - lastCacheUpdate < CACHE_DURATION_MS)) {
            return new ArrayList<>(cachedSymbols); // Return copy to protect cache
        }

        Set<String> symbols = new LinkedHashSet<>();
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
                                symbols.add(symbol);
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error listing uploaded files: {}", e.getMessage());
        }

        cachedSymbols = new ArrayList<>(symbols);
        lastCacheUpdate = now;
        return new ArrayList<>(cachedSymbols);
    }
}
