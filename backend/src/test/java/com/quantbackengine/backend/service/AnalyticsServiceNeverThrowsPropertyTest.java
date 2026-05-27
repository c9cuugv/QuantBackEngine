package com.quantbackengine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based test verifying that AnalyticsService.runQuantstats() never throws.
 *
 * <p><b>Property 7: runQuantstats() never throws</b><br>
 * <b>Validates: Requirement 6.2</b>
 */
class AnalyticsServiceNeverThrowsPropertyTest {

    private PythonBridgeService mockBridge;
    private DefaultAnalyticsService service;

    @BeforeProperty
    void setUp() {
        mockBridge = mock(PythonBridgeService.class);
        service = new DefaultAnalyticsService(mockBridge, new ObjectMapper());
    }

    /**
     * <b>Property 7: runQuantstats() never throws</b><br>
     * <b>Validates: Requirement 6.2</b>
     *
     * <p>For any QuantstatsRequest input (including null fields, empty maps, blank strings),
     * runQuantstats() SHALL always return a non-null QuantstatsResult and SHALL NOT throw.
     */
    @Property(tries = 200)
    void runQuantstats_neverThrows_bridgeAvailable(
            @ForAll("arbitraryRequests") QuantstatsRequest request) {

        // Bridge available but script throws — use doThrow to avoid triggering stub on re-entry
        when(mockBridge.isAvailable()).thenReturn(true);
        doThrow(new RuntimeException("simulated script failure"))
                .when(mockBridge).invoke(any(), any());

        QuantstatsResult result = service.runQuantstats(request);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
    }

    @Property(tries = 200)
    void runQuantstats_neverThrows_bridgeUnavailable(
            @ForAll("arbitraryRequests") QuantstatsRequest request) {

        when(mockBridge.isAvailable()).thenReturn(false);

        QuantstatsResult result = service.runQuantstats(request);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        // Bridge unavailable — no subprocess spawned
        verify(mockBridge, never()).invoke(any(), any());
    }

    @Property(tries = 50)
    void runQuantstats_neverThrows_nullRequest() {
        when(mockBridge.isAvailable()).thenReturn(true);
        doThrow(new RuntimeException("simulated failure"))
                .when(mockBridge).invoke(any(), any());

        QuantstatsResult result = service.runQuantstats(null);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<QuantstatsRequest> arbitraryRequests() {
        Arbitrary<String> nullableString = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(0)
                .ofMaxLength(20)
                .injectNull(0.2);

        Arbitrary<Double> riskFreeRate = Arbitraries.doubles().between(-1.0, 1.0);

        Arbitrary<Map<String, Double>> tickersWeights = Arbitraries.maps(
                Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(5),
                Arbitraries.doubles().between(0.0, 1.0)
        ).ofMinSize(0).ofMaxSize(5).injectNull(0.1);

        return Combinators.combine(tickersWeights, nullableString, nullableString, riskFreeRate, nullableString)
                .as(QuantstatsRequest::new);
    }
}
