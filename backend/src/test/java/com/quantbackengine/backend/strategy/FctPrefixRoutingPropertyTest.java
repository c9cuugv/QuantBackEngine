package com.quantbackengine.backend.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.service.BacktestService;
import com.quantbackengine.backend.service.MarketDataService;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test verifying that strategy IDs with the {@code fct:} prefix
 * are always routed to {@link PythonStrategyAdapter}.
 *
 * <p><b>Property 4: fct: prefix routes to PythonStrategyAdapter</b><br>
 * <b>Validates: Requirements 4.1, 4.2</b>
 */
class FctPrefixRoutingPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * <b>Property 4: fct: prefix routes to PythonStrategyAdapter</b><br>
     * <b>Validates: Requirements 4.1, 4.2</b>
     *
     * <p>For any strategy ID starting with {@code fct:}, BacktestService SHALL call
     * {@code PythonStrategyAdapter.runPythonBacktest()} and NOT the TA4J path.
     */
    @Property(tries = 100)
    void fctPrefixAlwaysRoutesToPythonAdapter(@ForAll("fctStrategyIds") String strategyId) {
        // Arrange
        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        StrategyRegistry mockRegistry = mock(StrategyRegistry.class);
        MarketDataService mockMarketData = mock(MarketDataService.class);

        PythonStrategyAdapter spyAdapter = spy(
                new PythonStrategyAdapter("test", "algo_trading/test.py", mockBridge, MAPPER));

        BacktestResponse fakeResponse = BacktestResponse.builder()
                .id("test-id")
                .symbol("AAPL")
                .strategy(strategyId)
                .metrics(BacktestResponse.MetricsDto.builder().build())
                .trades(java.util.List.of())
                .equityCurve(java.util.List.of())
                .candles(java.util.List.of())
                .build();

        doReturn(fakeResponse).when(spyAdapter).runPythonBacktest(any());
        when(mockRegistry.getStrategy(strategyId)).thenReturn(Optional.of(spyAdapter));

        BacktestService service = new BacktestService(mockRegistry, mockMarketData);
        ReflectionTestUtils.setField(service, "defaultInitialCapital", 100000.0);
        ReflectionTestUtils.setField(service, "defaultCommissionRate", 0.001);
        ReflectionTestUtils.setField(service, "riskFreeRate", 0.02);

        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy(strategyId)
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .initialCapital(100000.0)
                .build();

        // Act
        BacktestResponse result = service.runBacktest(request);

        // Assert — Python path was taken
        verify(spyAdapter, times(1)).runPythonBacktest(any());
        // TA4J path never invoked (no market data fetched)
        verify(mockMarketData, never()).getBarSeries(any(), any(), any());
        assertThat(result).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> fctStrategyIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(suffix -> "fct:" + suffix);
    }
}
