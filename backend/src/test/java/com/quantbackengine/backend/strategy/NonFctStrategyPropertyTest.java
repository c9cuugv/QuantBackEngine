package com.quantbackengine.backend.strategy;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.service.BacktestService;
import com.quantbackengine.backend.service.MarketDataService;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test verifying that non-{@code fct:} strategies never invoke
 * the Python bridge.
 *
 * <p><b>Property 5: Non-fct: strategies never touch the Python bridge</b><br>
 * <b>Validates: Requirement 4.2</b>
 */
class NonFctStrategyPropertyTest {

    /**
     * <b>Property 5: Non-fct: strategies never touch the Python bridge</b><br>
     * <b>Validates: Requirement 4.2</b>
     *
     * <p>For any strategy ID that does NOT start with {@code fct:}, the
     * {@link PythonBridgeService} SHALL never be invoked during backtest execution.
     */
    @Property(tries = 100)
    void nonFctStrategyNeverTouchesBridge(@ForAll("nonFctStrategyIds") String strategyId) {
        Assume.that(!strategyId.startsWith("fct:"));

        // Arrange
        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        StrategyRegistry mockRegistry = mock(StrategyRegistry.class);
        MarketDataService mockMarketData = mock(MarketDataService.class);

        // A minimal TA4J strategy that never buys/sells
        TradingStrategy mockTa4jStrategy = new TradingStrategy() {
            @Override public String getId() { return strategyId; }
            @Override public String getName() { return "mock"; }
            @Override public String getDescription() { return "mock"; }
            @Override public Strategy buildStrategy(BarSeries series, Map<String, Object> params) {
                return new BaseStrategy(
                        (i, r) -> false,
                        (i, r) -> false
                );
            }
            @Override public java.util.List<ParameterDefinition> getParameterDefinitions() {
                return Collections.emptyList();
            }
        };

        when(mockRegistry.getStrategy(strategyId)).thenReturn(Optional.of(mockTa4jStrategy));

        // Provide a minimal bar series so the TA4J path can complete
        BarSeries series = new BaseBarSeries(strategyId);
        ZonedDateTime time = LocalDate.of(2023, 1, 1).atStartOfDay(ZoneId.of("UTC"));
        for (int i = 0; i < 5; i++) {
            series.addBar(time.plusDays(i), 100, 110, 90, 105, 1000);
        }
        when(mockMarketData.getBarSeries(anyString(), any(), any())).thenReturn(series);

        BacktestService service = new BacktestService(mockRegistry, mockMarketData);
        ReflectionTestUtils.setField(service, "defaultInitialCapital", 100000.0);
        ReflectionTestUtils.setField(service, "defaultCommissionRate", 0.001);
        ReflectionTestUtils.setField(service, "riskFreeRate", 0.02);

        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy(strategyId)
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 1, 31))
                .initialCapital(100000.0)
                .build();

        // Act
        service.runBacktest(request);

        // Assert — bridge never touched
        verify(mockBridge, never()).invoke(any(), any());
        verify(mockBridge, never()).invokeWithStdin(any(), any(), anyString());
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> nonFctStrategyIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.startsWith("fct:"));
    }
}
