package com.quantbackengine.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;
import com.quantbackengine.backend.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void bridgeUnavailable_returnsSuccessHttpWithFailureResult() throws Exception {
        when(analyticsService.runQuantstats(any()))
                .thenReturn(new QuantstatsResult(false, "stats", Map.of()));

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 1.0), "SPY", "1y", 0.02, "stats");

        mockMvc.perform(post("/api/v1/analytics/quantstats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.action").value("stats"));
    }

    @Test
    void scriptFailure_returnsSuccessHttpWithFailureResult() throws Exception {
        when(analyticsService.runQuantstats(any()))
                .thenReturn(new QuantstatsResult(false, "returns", Map.of()));

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 1.0), "SPY", "1y", 0.02, "returns");

        mockMvc.perform(post("/api/v1/analytics/quantstats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void happyPath_returnsSuccessResult() throws Exception {
        Map<String, Object> data = Map.of("sharpe", 1.5, "max_drawdown", -0.12);
        when(analyticsService.runQuantstats(any()))
                .thenReturn(new QuantstatsResult(true, "stats", data));

        QuantstatsRequest request = new QuantstatsRequest(
                Map.of("AAPL", 0.6, "MSFT", 0.4), "SPY", "1y", 0.02, "stats");

        mockMvc.perform(post("/api/v1/analytics/quantstats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("stats"))
                .andExpect(jsonPath("$.data.sharpe").value(1.5));
    }
}
