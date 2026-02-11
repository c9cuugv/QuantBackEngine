package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.dto.StrategyDto;
import com.quantbackengine.backend.service.BacktestService;
import com.quantbackengine.backend.strategy.StrategyRegistry;
import com.quantbackengine.backend.strategy.TradingStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for backtesting operations.
 */
@RestController
@RequestMapping("/api/v1/backtest")
@RequiredArgsConstructor
@Tag(name = "Backtest", description = "Backtesting API")
public class BacktestController {

    private final BacktestService backtestService;
    private final StrategyRegistry strategyRegistry;

    @PostMapping("/run")
    @Operation(summary = "Run a backtest", description = "Execute a trading strategy on historical data")
    public ResponseEntity<BacktestResponse> runBacktest(@Valid @RequestBody BacktestRequest request) {
        BacktestResponse response = backtestService.runBacktest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/strategies")
    @Operation(summary = "List available strategies", description = "Get all available trading strategies and their parameters")
    public ResponseEntity<List<StrategyDto>> listStrategies() {
        List<StrategyDto> strategies = strategyRegistry.getAllStrategies().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(strategies);
    }

    @GetMapping("/strategies/{id}")
    @Operation(summary = "Get strategy details", description = "Get details of a specific trading strategy")
    public ResponseEntity<StrategyDto> getStrategy(@PathVariable String id) {
        return strategyRegistry.getStrategy(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private StrategyDto toDto(TradingStrategy strategy) {
        return StrategyDto.builder()
                .id(strategy.getId())
                .name(strategy.getName())
                .description(strategy.getDescription())
                .parameters(strategy.getParameterDefinitions().stream()
                        .map(p -> StrategyDto.ParameterDto.builder()
                                .name(p.name())
                                .type(p.type())
                                .defaultValue(p.defaultValue())
                                .minValue(p.minValue())
                                .maxValue(p.maxValue())
                                .description(p.description())
                                .build())
                        .toList())
                .build();
    }
}
