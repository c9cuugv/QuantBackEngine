package com.quantbackengine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.strategy.PythonStrategyAdapter;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    // -------------------------------------------------------------------------
    // fct: routing tests (Requirements 4.1, 4.2, 4.3)
    // -------------------------------------------------------------------------

    @Test
    void fctPrefix_routesToPythonAdapter_ta4jNotInvoked() {
        // Arrange
        String strategyId = "fct:my_python_strat";
        BacktestResponse expectedResponse = BacktestResponse.builder()
                .id("test-id")
                .symbol("AAPL")
                .strategy(strategyId)
                .metrics(BacktestResponse.MetricsDto.builder().build())
                .trades(List.of())
                .equityCurve(List.of())
                .candles(List.of())
                .build();

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        PythonStrategyAdapter spyAdapter = spy(
                new PythonStrategyAdapter("my_python_strat", "algo_trading/test.py",
                        mockBridge, new ObjectMapper()));
        doReturn(expectedResponse).when(spyAdapter).runPythonBacktest(any());

        when(strategyRegistry.getStrategy(strategyId)).thenReturn(Optional.of(spyAdapter));

        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy(strategyId)
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .initialCapital(100000.0)
                .build();

        // Act
        BacktestResponse result = backtestService.runBacktest(request);

        // Assert — Python path taken, TA4J market data never fetched
        verify(spyAdapter, times(1)).runPythonBacktest(any());
        verify(marketDataService, never()).getBarSeries(any(), any(), any());
        assertNotNull(result);
        assertEquals(strategyId, result.getStrategy());
    }

    @Test
    void nonFctPrefix_usesTA4JPath_bridgeNotInvoked() {
        // Arrange
        String strategyId = "sma";
        TradingStrategy mockTa4jStrategy = new TradingStrategy() {
            @Override public String getId() { return strategyId; }
            @Override public String getName() { return "SMA"; }
            @Override public String getDescription() { return "SMA"; }
            @Override public Strategy buildStrategy(BarSeries series, Map<String, Object> params) {
                return new BaseStrategy((i, r) -> false, (i, r) -> false);
            }
            @Override public List<ParameterDefinition> getParameterDefinitions() {
                return Collections.emptyList();
            }
        };

        when(strategyRegistry.getStrategy(strategyId)).thenReturn(Optional.of(mockTa4jStrategy));

        BarSeries series = new BaseBarSeries(strategyId);
        ZonedDateTime time = LocalDate.of(2023, 1, 1).atStartOfDay(java.time.ZoneId.of("UTC"));
        for (int i = 0; i < 5; i++) {
            series.addBar(time.plusDays(i), 100, 110, 90, 105, 1000);
        }
        when(marketDataService.getBarSeries(any(), any(), any())).thenReturn(series);

        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy(strategyId)
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 1, 31))
                .initialCapital(100000.0)
                .build();

        // Act
        BacktestResponse result = backtestService.runBacktest(request);

        // Assert — TA4J path taken, market data was fetched
        verify(marketDataService, times(1)).getBarSeries(any(), any(), any());
        assertNotNull(result);
    }

    @Test
    void unknownFctId_throwsIllegalArgumentException() {
        // Arrange
        String unknownId = "fct:nonexistent";
        when(strategyRegistry.getStrategy(unknownId)).thenReturn(Optional.empty());

        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy(unknownId)
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .initialCapital(100000.0)
                .build();

        // Act & Assert — resolves to HTTP 400 via GlobalExceptionHandler
        assertThrows(IllegalArgumentException.class, () -> backtestService.runBacktest(request));
    }
}
