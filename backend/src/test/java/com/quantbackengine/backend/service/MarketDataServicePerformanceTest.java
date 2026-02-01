package com.quantbackengine.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketDataServicePerformanceTest {

    private MarketDataService marketDataService;

    @TempDir
    Path tempUploadDir;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService();
        ReflectionTestUtils.setField(marketDataService, "uploadDir", tempUploadDir.toString());
    }

    @Test
    void benchmarkGetAvailableSymbols() throws IOException {
        // 1. Setup - Create 1000 dummy files
        int fileCount = 1000;
        System.out.println("Creating " + fileCount + " dummy files...");
        for (int i = 0; i < fileCount; i++) {
            String symbol = "SYM" + i;
            Path file = tempUploadDir.resolve(symbol + ".csv");
            Files.writeString(file, "Date,Open,High,Low,Close,Volume\n");
        }
        System.out.println("Files created.");

        // 2. Warmup
        for (int i = 0; i < 10; i++) {
            marketDataService.getAvailableSymbols();
        }

        // 3. Measure
        int iterations = 50;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            List<String> symbols = marketDataService.getAvailableSymbols();
            // Basic assertion to ensure it's actually working
            assertFalse(symbols.isEmpty());
        }
        long end = System.nanoTime();

        // 4. Report
        double totalTimeMs = (end - start) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / iterations;

        System.out.println("Benchmark Results for getAvailableSymbols:");
        System.out.println("Total files: " + fileCount);
        System.out.println("Iterations: " + iterations);
        System.out.println("Total Time: " + totalTimeMs + " ms");
        System.out.println("Average Time per call: " + avgTimeMs + " ms");
    }

    @Test
    void benchmarkGetBarSeries() throws IOException {
        // 1. Setup - Create a large CSV file (5000 records)
        String symbol = "BENCH";
        Path file = tempUploadDir.resolve(symbol + ".csv");

        List<String> lines = new ArrayList<>();
        lines.add("Date,Open,High,Low,Close,Volume");

        LocalDate date = LocalDate.of(2000, 1, 1);
        for (int i = 0; i < 5000; i++) {
            lines.add(String.format("%s,100.0,105.0,95.0,100.0,1000", date.plusDays(i)));
        }
        Files.write(file, lines);
        System.out.println("Created benchmark file with 5000 records.");

        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 1);

        // 2. Warmup
        for (int i = 0; i < 5; i++) {
            marketDataService.getBarSeries(symbol, start, end);
        }

        // 3. Measure
        int iterations = 100;
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BarSeries series = marketDataService.getBarSeries(symbol, start, end);
            assertFalse(series.isEmpty());
            assertEquals(symbol, series.getName());
        }
        long endTime = System.nanoTime();

        // 4. Report
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / iterations;

        System.out.println("Benchmark Results for getBarSeries:");
        System.out.println("Records: 5000");
        System.out.println("Iterations: " + iterations);
        System.out.println("Total Time: " + totalTimeMs + " ms");
        System.out.println("Average Time per call: " + avgTimeMs + " ms");
    }
}
