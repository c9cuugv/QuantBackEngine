package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

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
    void testRunBacktest_Success() {
        // Arrange
        String symbol = "AAPL";
        String strategyName = "TEST_STRATEGY";
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 10);

        // Mock Strategy
        TradingStrategy mockStrategy = new TradingStrategy() {
            @Override
            public String getId() { return "TEST"; }
            @Override
            public String getName() { return "Test"; }
            @Override
            public String getDescription() { return "Test"; }
            @Override
            public Strategy buildStrategy(BarSeries series, Map<String, Object> params) {
                return new BaseStrategy(
                    new Rule() {
                        @Override
                        public boolean isSatisfied(int index, TradingRecord record) {
                            return index % 2 == 0;
                        }
                    },
                    new Rule() {
                        @Override
                        public boolean isSatisfied(int index, TradingRecord record) {
                            return index % 2 != 0;
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

        // Mock Market Data
        BarSeries series = new BaseBarSeries(symbol);
        ZonedDateTime time = start.atStartOfDay(java.time.ZoneId.of("UTC"));
        for (int i = 0; i < 10; i++) {
            series.addBar(time.plusDays(i), 100 + i, 110 + i, 90 + i, 105 + i, 1000);
        }
        when(marketDataService.getBarSeries(eq(symbol), eq(start), eq(end))).thenReturn(series);

        BacktestRequest request = BacktestRequest.builder()
                .symbol(symbol)
                .strategy(strategyName)
                .startDate(start)
                .endDate(end)
                .initialCapital(10000.0)
                .build();

        // Act
        BacktestResponse response = backtestService.runBacktest(request);

        // Assert
        assertNotNull(response);
        assertEquals(symbol, response.getSymbol());
        assertNotNull(response.getMetrics());
        assertFalse(response.getTrades().isEmpty(), "Should have trades");
        assertEquals(5, response.getTrades().size(), "Should have 5 trades (Entry at 0,2,4,6,8; Exit at 1,3,5,7,9)");
    }
}
