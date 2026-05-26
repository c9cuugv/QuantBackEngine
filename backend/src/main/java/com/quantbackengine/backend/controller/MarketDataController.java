package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.dto.DataSourceInfo;
import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.service.MarketDataService;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for market data operations.
 */
@RestController
@RequestMapping("/api/v1/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Market Data API")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final PythonMarketDataProvider pythonMarketDataProvider;
    private final PythonBridgeService pythonBridgeService;

    @GetMapping("/symbols")
    @Operation(summary = "List available symbols", description = "Get all available stock symbols")
    public ResponseEntity<List<String>> listSymbols() {
        return ResponseEntity.ok(marketDataService.getAvailableSymbols());
    }

    @GetMapping("/python/{symbol}")
    @Operation(summary = "Fetch OHLCV bars via Python", description = "Fetch historical OHLCV data via the Python bridge")
    public ResponseEntity<?> getPythonBars(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "yfinance") String source,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        if (!pythonBridgeService.isAvailable()) {
            log.warn("Python bridge unavailable for /python/{}", symbol);
            return ResponseEntity.badRequest().body("Python bridge is unavailable");
        }

        List<OhlcvBar> bars = pythonMarketDataProvider.fetchHistorical(symbol, start, end, source);
        if (bars.isEmpty()) {
            log.warn("No market data returned for symbol '{}' from source '{}'", symbol, source);
            return ResponseEntity.badRequest().body("No market data available for " + symbol);
        }

        return ResponseEntity.ok(bars);
    }

    @GetMapping("/sources")
    @Operation(summary = "List Python data sources", description = "List all available Python data sources with availability status")
    public ResponseEntity<List<DataSourceInfo>> listSources() {
        return ResponseEntity.ok(pythonMarketDataProvider.listSources());
    }
}
