package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.strategy.StrategyRegistry;
import com.quantbackengine.backend.strategy.TradingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestServiceBenchmarkTest {

    @Mock
    private StrategyRegistry strategyRegistry;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backtestService, "defaultInitialCapital", 100000.0);
        ReflectionTestUtils.setField(backtestService, "defaultCommissionRate", 0.001);
        ReflectionTestUtils.setField(backtestService, "riskFreeRate", 0.02);
    }

    @Test
    void benchmarkRunBacktest() {
        // 1. Setup
        int barCount = 1_000_000;
        String symbol = "BENCHMARK";
        String strategyName = "BENCH_STRATEGY";
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 1);

        // Mock Strategy
        TradingStrategy mockStrategy = new TradingStrategy() {
            @Override
            public String getId() { return "BENCH"; }
            @Override
            public String getName() { return "Bench"; }
            @Override
            public String getDescription() { return "Bench"; }
            @Override
            public Strategy buildStrategy(BarSeries series, Map<String, Object> params) {
                // Simple strategy: Enter if index is even, Exit if odd
                return new BaseStrategy(
                    new Rule() {
                        @Override
                        public boolean isSatisfied(int index, TradingRecord record) {
                            return index % 100 == 0; // Trade occasionally
                        }
                    },
                    new Rule() {
                        @Override
                        public boolean isSatisfied(int index, TradingRecord record) {
                            return index % 100 == 50;
                        }
                    }
                );
            }
            @Override
            public java.util.List<ParameterDefinition> getParameterDefinitions() {
                return Collections.emptyList();
            }
        };

        when(strategyRegistry.getStrategy(strategyName)).thenReturn(Optional.of(mockStrategy));

        // Mock Market Data (Large dataset)
        System.out.println("Generating " + barCount + " bars...");
        BarSeries series = new BaseBarSeries(symbol);
        ZonedDateTime time = start.atStartOfDay(ZoneId.of("UTC"));
        for (int i = 0; i < barCount; i++) {
            series.addBar(time.plusMinutes(i), 100 + (i * 0.01), 101 + (i * 0.01), 99 + (i * 0.01), 100.5 + (i * 0.01), 1000);
        }
        System.out.println("Bars generated.");

        when(marketDataService.getBarSeries(eq(symbol), any(), any())).thenReturn(series);

        BacktestRequest request = BacktestRequest.builder()
                .symbol(symbol)
                .strategy(strategyName)
                .startDate(start)
                .endDate(end)
                .initialCapital(100000.0)
                .build();

        // 2. Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 5; i++) {
            backtestService.runBacktest(request);
        }

        // 3. Measure
        int iterations = 10;
        System.out.println("Running benchmark (" + iterations + " iterations)...");
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            backtestService.runBacktest(request);
        }
        long endTime = System.nanoTime();

        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / iterations;

        System.out.println("Benchmark Results:");
        System.out.println("Bar count: " + barCount);
        System.out.println("Avg Time: " + avgTimeMs + " ms");
    }
}
