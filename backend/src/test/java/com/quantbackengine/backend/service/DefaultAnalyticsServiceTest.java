package com.quantbackengine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;
import com.quantbackengine.backend.exception.PythonBridgeException;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAnalyticsServiceTest {

    @Mock
    private PythonBridgeService bridge;

    private DefaultAnalyticsService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DefaultAnalyticsService(bridge, objectMapper);
    }

    @Test
    void bridgeUnavailable_returnsFailureResult_noSubprocessSpawned() {
        when(bridge.isAvailable()).thenReturn(false);

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 1.0), "SPY", "1y", 0.02, "stats");

        QuantstatsResult result = service.runQuantstats(request);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.action()).isEqualTo("stats");
        verify(bridge, never()).invoke(any(), any());
    }

    @Test
    void scriptFailure_returnsFailureResult_noExceptionPropagated() {
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.invoke(any(), any()))
                .thenThrow(new PythonBridgeException("script error"));

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 1.0), "SPY", "1y", 0.02, "stats");

        QuantstatsResult result = service.runQuantstats(request);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.action()).isEqualTo("stats");
    }

    @Test
    void happyPath_bridgeReturnsValidJson_returnsSuccessResult() throws Exception {
        when(bridge.isAvailable()).thenReturn(true);

        ObjectNode jsonResult = objectMapper.createObjectNode();
        jsonResult.put("sharpe", 1.5);
        jsonResult.put("max_drawdown", -0.12);
        when(bridge.invoke(any(), any())).thenReturn(jsonResult);

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 0.6, "MSFT", 0.4), "SPY", "1y", 0.02, "stats");

        QuantstatsResult result = service.runQuantstats(request);

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.action()).isEqualTo("stats");
        assertThat(result.data()).isNotNull();
        assertThat(result.data()).containsKey("sharpe");
    }

    @Test
    void nullRequest_returnsFailureResult_noException() {
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.invoke(any(), any()))
                .thenThrow(new RuntimeException("unexpected"));

        QuantstatsResult result = service.runQuantstats(null);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
    }
}
