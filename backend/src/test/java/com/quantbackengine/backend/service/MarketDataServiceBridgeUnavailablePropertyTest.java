package com.quantbackengine.backend.service;

import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import net.jqwik.api.*;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test verifying that when {@code isAvailable()=false} no subprocesses
 * are spawned in {@link MarketDataService}.
 *
 * <p><b>Property 7: isAvailable()=false means zero subprocesses</b><br>
 * <b>Validates: Requirements 1.7, 2.2, 6.3</b>
 */
class MarketDataServiceBridgeUnavailablePropertyTest {

    /**
     * <b>Property 7: isAvailable()=false means zero subprocesses</b><br>
     * <b>Validates: Requirements 1.7, 2.2, 6.3</b>
     *
     * <p>When {@code isAvailable()=false}, {@code MarketDataService.getBarSeries()} SHALL
     * never call {@code invoke()}, {@code invokeWithStdin()}, or {@code fetchHistorical()}
     * regardless of the symbol or date range.
     */
    @Property(tries = 100)
    void whenBridgeUnavailableNoBridgeMethodIsInvoked(
            @ForAll("validSymbols") String symbol,
            @ForAll("validStartDates") LocalDate start) {

        LocalDate end = start.plusMonths(6);

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.isAvailable()).thenReturn(false);

        PythonMarketDataProvider mockProvider = mock(PythonMarketDataProvider.class);

        MarketDataService service = new MarketDataService(mockProvider, mockBridge);

        BarSeries result = service.getBarSeries(symbol, start, end);

        // No subprocess must have been spawned
        verify(mockBridge, never()).invoke(anyString(), any());
        verify(mockBridge, never()).invokeWithStdin(anyString(), any(), anyString());
        verify(mockProvider, never()).fetchHistorical(anyString(), any(), any(), anyString());

        // Result must be a valid (empty) BarSeries — never null
        assertNotNull(result, "getBarSeries() must never return null");
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> validSymbols() {
        return Arbitraries.of("AAPL", "MSFT", "TSLA", "GOOGL", "AMZN",
                              "NVDA", "META", "SPY", "QQQ", "BTC-USD");
    }

    @Provide
    Arbitrary<LocalDate> validStartDates() {
        return Arbitraries.integers()
                .between(2010, 2023)
                .flatMap(year ->
                        Arbitraries.integers().between(1, 12).flatMap(month -> {
                            int maxDay = LocalDate.of(year, month, 1).lengthOfMonth();
                            return Arbitraries.integers()
                                    .between(1, maxDay)
                                    .map(day -> LocalDate.of(year, month, day));
                        })
                );
    }
}
