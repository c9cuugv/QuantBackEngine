package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for market data operations.
 */
@RestController
@RequestMapping("/api/v1/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Market Data API")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/symbols")
    @Operation(summary = "List available symbols", description = "Get all available stock symbols")
    public ResponseEntity<List<String>> listSymbols() {
        return ResponseEntity.ok(marketDataService.getAvailableSymbols());
    }
}
