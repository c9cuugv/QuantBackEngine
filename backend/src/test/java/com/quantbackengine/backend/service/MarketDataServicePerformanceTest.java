package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataServicePerformanceTest {

    private MarketDataService marketDataService;
    private PythonMarketDataProvider mockProvider;
    private PythonBridgeService mockBridge;

    @BeforeEach
    void setUp() {
        mockProvider = mock(PythonMarketDataProvider.class);
        mockBridge = mock(PythonBridgeService.class);
        marketDataService = new MarketDataService(mockProvider, mockBridge);
    }

    @Test
    void benchmarkGetAvailableSymbols() {
        // Warmup
        for (int i = 0; i < 10; i++) {
            marketDataService.getAvailableSymbols();
        }

        int iterations = 50;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            List<String> symbols = marketDataService.getAvailableSymbols();
            assertFalse(symbols.isEmpty());
        }
        long end = System.nanoTime();

        double totalTimeMs = (end - start) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / iterations;
        System.out.println("Benchmark getAvailableSymbols: " + avgTimeMs + " ms/call");
    }

    @Test
    void benchmarkGetBarSeries() {
        String symbol = "BENCH";
        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 1);

        // Generate 5000 mock bars
        List<OhlcvBar> bars = new ArrayList<>();
        long baseTimestamp = start.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        for (int i = 0; i < 5000; i++) {
            bars.add(new OhlcvBar(symbol, baseTimestamp + i * 86_400_000L,
                    100.0, 105.0, 95.0, 100.0, 1000));
        }

        when(mockBridge.isAvailable()).thenReturn(true);
        when(mockProvider.fetchHistorical(anyString(), any(), any(), anyString())).thenReturn(bars);

        // Warmup
        for (int i = 0; i < 5; i++) {
            marketDataService.getBarSeries(symbol, start, end);
        }

        int iterations = 100;
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BarSeries series = marketDataService.getBarSeries(symbol, start, end);
            assertFalse(series.isEmpty());
            assertEquals(symbol, series.getName());
        }
        long endTime = System.nanoTime();

        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / iterations;
        System.out.println("Benchmark getBarSeries (5000 bars): " + avgTimeMs + " ms/call");
    }
}
