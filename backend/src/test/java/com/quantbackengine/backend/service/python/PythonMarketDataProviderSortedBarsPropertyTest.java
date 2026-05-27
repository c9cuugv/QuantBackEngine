package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quantbackengine.backend.dto.OhlcvBar;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Property-based test for bars sorted by timestamp in DefaultPythonMarketDataProvider.
 *
 * <p><b>Property 2: Python fallback bars are sorted</b><br>
 * <b>Validates: Requirement 2.3</b>
 *
 * <p>For any valid OHLCV JSON response from a Python data script, the resulting
 * {@link OhlcvBar} list SHALL be sorted in ascending order by timestamp.
 */
class PythonMarketDataProviderSortedBarsPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds a valid OHLCV JSON object node with the given timestamp.
     * Prices are constructed so the OHLCV invariant holds:
     *   high >= max(open, close), low <= min(open, close), volume >= 0.
     */
    private ObjectNode buildBarNode(long timestamp, double base, long volume) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("timestamp", timestamp);
        node.put("open",  base);
        node.put("close", base);
        node.put("high",  base + 1.0);   // high > open == close
        node.put("low",   base - 1.0);   // low  < open == close
        node.put("volume", volume);
        return node;
    }

    /**
     * <b>Property 2: Python fallback bars are sorted</b><br>
     * <b>Validates: Requirement 2.3</b>
     *
     * <p>For any arbitrary list of valid OHLCV JSON nodes with random timestamps,
     * {@code DefaultPythonMarketDataProvider.fetchHistorical()} SHALL return bars
     * sorted ascending by {@link OhlcvBar#timestamp()}.
     */
    @Property(tries = 100)
    void fetchHistoricalReturnsBarsAscendingByTimestamp(
            @ForAll("validOhlcvTimestamps") List<Long> timestamps) {

        // Build a JSON array from the generated timestamps
        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (int i = 0; i < timestamps.size(); i++) {
            jsonArray.add(buildBarNode(timestamps.get(i), 100.0 + i, 1000L + i));
        }

        // Mock the bridge to return our generated JSON array
        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(jsonArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        // The result must be sorted ascending by timestamp
        for (int i = 1; i < bars.size(); i++) {
            assertTrue(
                    bars.get(i).timestamp() >= bars.get(i - 1).timestamp(),
                    "Bar at index " + i + " (ts=" + bars.get(i).timestamp() +
                    ") must be >= bar at index " + (i - 1) + " (ts=" + bars.get(i - 1).timestamp() + ")"
            );
        }
    }

    /**
     * <b>Property 2: Sorted order is preserved regardless of input order</b><br>
     * <b>Validates: Requirement 2.3</b>
     *
     * <p>Even when the JSON array is provided in reverse or random order,
     * the returned list must be sorted ascending by timestamp.
     */
    @Property(tries = 100)
    void fetchHistoricalSortsEvenWhenInputIsReversed(
            @ForAll("validOhlcvTimestamps") List<Long> timestamps) {

        // Sort descending to give the provider the worst-case ordering
        List<Long> reversed = timestamps.stream().sorted((a, b) -> Long.compare(b, a)).toList();

        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (int i = 0; i < reversed.size(); i++) {
            jsonArray.add(buildBarNode(reversed.get(i), 50.0 + i, 500L + i));
        }

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(jsonArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        for (int i = 1; i < bars.size(); i++) {
            assertTrue(
                    bars.get(i).timestamp() >= bars.get(i - 1).timestamp(),
                    "Bars must be sorted ascending even when input was reversed"
            );
        }
    }

    /**
     * <b>Property 2: Empty input produces empty sorted output</b><br>
     * <b>Validates: Requirement 2.3</b>
     */
    @Property(tries = 10)
    void fetchHistoricalWithEmptyArrayReturnsSortedEmptyList() {
        ArrayNode emptyArray = objectMapper.createArrayNode();

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(emptyArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        assertNotNull(bars, "Result must not be null");
        assertTrue(bars.isEmpty(), "Empty input must produce empty output");
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates arbitrary lists of distinct timestamps (epoch millis) of size 0–50.
     * Using distinct values avoids ambiguity in sort-order assertions.
     */
    @Provide
    Arbitrary<List<Long>> validOhlcvTimestamps() {
        // Epoch millis in a realistic range: 2000-01-01 to 2030-01-01
        long minTs = 946684800000L;  // 2000-01-01T00:00:00Z
        long maxTs = 1893456000000L; // 2030-01-01T00:00:00Z

        return Arbitraries.longs()
                .between(minTs, maxTs)
                .list()
                .ofMinSize(0)
                .ofMaxSize(50)
                .uniqueElements();
    }
}
