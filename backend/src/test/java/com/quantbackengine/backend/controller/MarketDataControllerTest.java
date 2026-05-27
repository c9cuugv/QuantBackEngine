package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.dto.DataSourceInfo;
import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.service.MarketDataService;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MarketDataControllerTest {

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private PythonMarketDataProvider pythonMarketDataProvider;

    @Mock
    private PythonBridgeService pythonBridgeService;

    @InjectMocks
    private MarketDataController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void pythonSymbol_bridgeUnavailable_returns400() throws Exception {
        when(pythonBridgeService.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/v1/market-data/python/AAPL")
                        .param("source", "yfinance")
                        .param("start", "2023-01-01")
                        .param("end", "2023-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pythonSymbol_noDataReturned_returns400() throws Exception {
        when(pythonBridgeService.isAvailable()).thenReturn(true);
        when(pythonMarketDataProvider.fetchHistorical(anyString(), any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market-data/python/AAPL")
                        .param("source", "yfinance")
                        .param("start", "2023-01-01")
                        .param("end", "2023-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pythonSymbol_dataReturned_returns200WithBars() throws Exception {
        when(pythonBridgeService.isAvailable()).thenReturn(true);

        OhlcvBar bar = new OhlcvBar("AAPL", 1672531200000L, 130.0, 135.0, 128.0, 132.0, 1000000L);
        when(pythonMarketDataProvider.fetchHistorical(anyString(), any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(List.of(bar));

        mockMvc.perform(get("/api/v1/market-data/python/AAPL")
                        .param("source", "yfinance")
                        .param("start", "2023-01-01")
                        .param("end", "2023-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].close").value(132.0));
    }

    @Test
    void sources_returnsDataSourceInfoList() throws Exception {
        List<DataSourceInfo> sources = List.of(
                new DataSourceInfo("yfinance", "Yahoo Finance", "yfinance_data.py", true),
                new DataSourceInfo("fred", "FRED", "fred_data.py", false)
        );
        when(pythonMarketDataProvider.listSources()).thenReturn(sources);

        mockMvc.perform(get("/api/v1/market-data/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("yfinance"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].id").value("fred"))
                .andExpect(jsonPath("$[1].available").value(false));
    }
}
