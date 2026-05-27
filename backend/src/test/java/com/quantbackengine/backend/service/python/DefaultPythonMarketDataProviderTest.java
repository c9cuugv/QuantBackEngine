package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.DataSourceInfo;
import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.exception.PythonBridgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultPythonMarketDataProvider}.
 *
 * <p>Mocks {@link PythonBridgeService} to return canned JSON and asserts correct
 * {@link OhlcvBar} mapping, sort order, and invariant filtering.
 *
 * <p><b>Validates: Requirements 2.3, 2.4</b>
 */
@ExtendWith(MockitoExtension.class)
class DefaultPythonMarketDataProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PythonBridgeService bridge;

    private DefaultPythonMarketDataProvider provider;

    private final LocalDate START = LocalDate.of(2024, 1, 1);
    private final LocalDate END   = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        provider = new DefaultPythonMarketDataProvider(bridge);
    }

    // =========================================================================
    // 1. Happy path — correct mapping and ascending sort order
    // =========================================================================

    /**
     * When the bridge returns a valid JSON array, all fields must be mapped correctly
     * and bars must be sorted ascending by timestamp.
     *
     * <b>Validates: Requirements 2.3, 2.4</b>
     */
    @Test
    void fetchHistorical_validJson_mapsAllFieldsAndSortsAscending() throws Exception {
        String json = """
                [
                  {"symbol":"AAPL","timestamp":1000,"open":100.0,"high":110.0,"low":90.0,"close":105.0,"volume":1000},
                  {"symbol":"AAPL","timestamp":3000,"open":106.0,"high":115.0,"low":104.0,"close":112.0,"volume":2000},
                  {"symbol":"AAPL","timestamp":2000,"open":105.0,"high":112.0,"low":103.0,"close":108.0,"volume":1500}
                ]
                """;
        when(bridge.invoke(anyString(), anyList())).thenReturn(MAPPER.readTree(json));

        List<OhlcvBar> bars = provider.fetchHistorical("AAPL", START, END, "yfinance");

        assertEquals(3, bars.size());

        // Verify ascending sort by timestamp
        assertEquals(1000L, bars.get(0).timestamp());
        assertEquals(2000L, bars.get(1).timestamp());
        assertEquals(3000L, bars.get(2).timestamp());

        // Verify all fields on the first bar
        OhlcvBar first = bars.get(0);
        assertEquals("AAPL",  first.symbol());
        assertEquals(1000L,   first.timestamp());
        assertEquals(100.0,   first.open(),   1e-9);
        assertEquals(110.0,   first.high(),   1e-9);
        assertEquals(90.0,    first.low(),    1e-9);
        assertEquals(105.0,   first.close(),  1e-9);
        assertEquals(1000L,   first.volume());
    }

    // =========================================================================
    // 2. Mixed valid/invalid bars — invariant filtering
    // =========================================================================

    /**
     * Bars that violate the OHLCV invariant must be dropped; only valid bars returned.
     *
     * <b>Validates: Requirement 2.4</b>
     */
    @Test
    void fetchHistorical_mixedBars_onlyValidBarsReturned() throws Exception {
        String json = """
                [
                  {"timestamp":1000,"open":100.0,"high":110.0,"low":90.0,"close":105.0,"volume":500},
                  {"timestamp":2000,"open":100.0,"high":95.0,"low":90.0,"close":98.0,"volume":500},
                  {"timestamp":3000,"open":100.0,"high":110.0,"low":105.0,"close":108.0,"volume":500},
                  {"timestamp":4000,"open":100.0,"high":110.0,"low":90.0,"close":105.0,"volume":-1},
                  {"timestamp":5000,"open":100.0,"high":110.0,"low":90.0,"close":105.0,"volume":200}
                ]
                """;
        // Bar at ts=2000: high(95) < open(100) → invalid
        // Bar at ts=3000: low(105) > close(108) is fine, but low(105) > open(100) → invalid
        // Bar at ts=4000: volume < 0 → invalid
        when(bridge.invoke(anyString(), anyList())).thenReturn(MAPPER.readTree(json));

        List<OhlcvBar> bars = provider.fetchHistorical("MSFT", START, END, "yfinance");

        // Only ts=1000 and ts=5000 are valid
        assertEquals(2, bars.size());
        assertEquals(1000L, bars.get(0).timestamp());
        assertEquals(5000L, bars.get(1).timestamp());
    }

    // =========================================================================
    // 3. Empty JSON array → empty list
    // =========================================================================

    /**
     * An empty JSON array from the bridge must produce an empty list.
     *
     * <b>Validates: Requirement 2.3</b>
     */
    @Test
    void fetchHistorical_emptyJsonArray_returnsEmptyList() throws Exception {
        when(bridge.invoke(anyString(), anyList())).thenReturn(MAPPER.readTree("[]"));

        List<OhlcvBar> bars = provider.fetchHistorical("TSLA", START, END, "yfinance");

        assertNotNull(bars);
        assertTrue(bars.isEmpty());
    }

    // =========================================================================
    // 4. Unknown source → empty list, bridge never called
    // =========================================================================

    /**
     * When an unknown source is requested, the bridge must not be called and an
     * empty list must be returned.
     *
     * <b>Validates: Requirement 2.3</b>
     */
    @Test
    void fetchHistorical_unknownSource_returnsEmptyListWithoutCallingBridge() {
        List<OhlcvBar> bars = provider.fetchHistorical("AAPL", START, END, "unknown_source");

        assertNotNull(bars);
        assertTrue(bars.isEmpty());
        verifyNoInteractions(bridge);
    }

    // =========================================================================
    // 5. Bridge throws PythonBridgeException → empty list, no exception propagated
    // =========================================================================

    /**
     * When the bridge throws {@link PythonBridgeException}, the provider must swallow
     * it and return an empty list.
     *
     * <b>Validates: Requirement 2.3</b>
     */
    @Test
    void fetchHistorical_bridgeThrows_returnsEmptyListWithoutPropagating() throws Exception {
        when(bridge.invoke(anyString(), anyList()))
                .thenThrow(new PythonBridgeException("script failed"));

        List<OhlcvBar> bars = assertDoesNotThrow(
                () -> provider.fetchHistorical("AAPL", START, END, "yfinance"),
                "PythonBridgeException must not propagate out of fetchHistorical"
        );

        assertNotNull(bars);
        assertTrue(bars.isEmpty());
    }

    // =========================================================================
    // 6. listSources() when bridge available → all 4 sources with available=true
    // =========================================================================

    /**
     * When the bridge is available, {@code listSources()} must return all 4 known
     * sources with {@code available=true}.
     *
     * <b>Validates: Requirement 3.1</b>
     */
    @Test
    void listSources_bridgeAvailable_returnsFourSourcesAllAvailable() {
        when(bridge.isAvailable()).thenReturn(true);

        List<DataSourceInfo> sources = provider.listSources();

        assertEquals(4, sources.size());
        assertTrue(sources.stream().allMatch(DataSourceInfo::available),
                "All sources should be available when bridge is available");

        // Verify expected source IDs are present
        List<String> ids = sources.stream().map(DataSourceInfo::id).toList();
        assertTrue(ids.contains("yfinance"));
        assertTrue(ids.contains("fred"));
        assertTrue(ids.contains("akshare"));
        assertTrue(ids.contains("alpha_vantage"));
    }

    // =========================================================================
    // 7. listSources() when bridge unavailable → all 4 sources with available=false
    // =========================================================================

    /**
     * When the bridge is unavailable, {@code listSources()} must return all 4 known
     * sources with {@code available=false}.
     *
     * <b>Validates: Requirement 3.1</b>
     */
    @Test
    void listSources_bridgeUnavailable_returnsFourSourcesAllUnavailable() {
        when(bridge.isAvailable()).thenReturn(false);

        List<DataSourceInfo> sources = provider.listSources();

        assertEquals(4, sources.size());
        assertTrue(sources.stream().noneMatch(DataSourceInfo::available),
                "All sources should be unavailable when bridge is unavailable");
    }
}
