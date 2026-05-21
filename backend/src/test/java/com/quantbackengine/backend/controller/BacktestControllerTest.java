package com.quantbackengine.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.exception.GlobalExceptionHandler;
import com.quantbackengine.backend.service.BacktestService;
import com.quantbackengine.backend.strategy.StrategyRegistry;
import com.quantbackengine.backend.strategy.TradingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BacktestControllerTest {

    @Mock
    private BacktestService backtestService;

    @Mock
    private StrategyRegistry strategyRegistry;

    @InjectMocks
    private BacktestController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // --- POST /api/v1/backtest/run ---

    @Test
    void runBacktest_validRequest_returns200WithBody() throws Exception {
        BacktestResponse response = BacktestResponse.builder()
                .id("test-id")
                .symbol("AAPL")
                .strategy("sma")
                .metrics(BacktestResponse.MetricsDto.builder().totalReturn(0.05).build())
                .trades(List.of())
                .equityCurve(List.of())
                .candles(List.of())
                .build();

        when(backtestService.runBacktest(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/backtest/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.strategy").value("sma"));
    }

    @Test
    void runBacktest_unknownStrategy_returns400() throws Exception {
        when(backtestService.runBacktest(any()))
                .thenThrow(new IllegalArgumentException("Unknown strategy: nonexistent"));

        mockMvc.perform(post("/api/v1/backtest/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unknown strategy: nonexistent"));
    }

    @Test
    void runBacktest_noMarketData_returns422() throws Exception {
        when(backtestService.runBacktest(any()))
                .thenThrow(new IllegalStateException("No market data available for AAPL"));

        mockMvc.perform(post("/api/v1/backtest/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void runBacktest_unexpectedException_returns500() throws Exception {
        when(backtestService.runBacktest(any()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        mockMvc.perform(post("/api/v1/backtest/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    // --- GET /api/v1/backtest/strategies ---

    @Test
    void listStrategies_noStrategies_returnsEmptyArray() throws Exception {
        when(strategyRegistry.getAllStrategies()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/backtest/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listStrategies_withStrategies_returnsMappedDtos() throws Exception {
        when(strategyRegistry.getAllStrategies())
                .thenReturn(List.of(stubbedStrategy("sma", "Simple Moving Average", "SMA crossover")));

        mockMvc.perform(get("/api/v1/backtest/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sma"))
                .andExpect(jsonPath("$[0].name").value("Simple Moving Average"))
                .andExpect(jsonPath("$[0].description").value("SMA crossover"));
    }

    // --- GET /api/v1/backtest/strategies/{id} ---

    @Test
    void getStrategy_found_returns200WithDto() throws Exception {
        when(strategyRegistry.getStrategy("sma"))
                .thenReturn(Optional.of(stubbedStrategy("sma", "SMA", "desc")));

        mockMvc.perform(get("/api/v1/backtest/strategies/sma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sma"))
                .andExpect(jsonPath("$.name").value("SMA"));
    }

    @Test
    void getStrategy_notFound_returns404() throws Exception {
        when(strategyRegistry.getStrategy("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/backtest/strategies/ghost"))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private BacktestRequest validRequest() {
        return BacktestRequest.builder()
                .symbol("AAPL")
                .strategy("sma")
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .initialCapital(100000.0)
                .build();
    }

    private TradingStrategy stubbedStrategy(String id, String name, String description) {
        return new TradingStrategy() {
            @Override public String getId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public Strategy buildStrategy(BarSeries series, Map<String, Object> params) { return null; }
            @Override public List<TradingStrategy.ParameterDefinition> getParameterDefinitions() { return Collections.emptyList(); }
        };
    }
}
