package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for all available trading strategies.
 * Auto-discovers strategies via Spring dependency injection.
 * Supports runtime registration of additional strategies (e.g. Python adapters).
 */
@Service
public class StrategyRegistry {

    private final Map<String, TradingStrategy> strategies;

    public StrategyRegistry(List<TradingStrategy> strategyList) {
        this.strategies = new ConcurrentHashMap<>(strategyList.stream()
                .collect(Collectors.toMap(
                        TradingStrategy::getId,
                        Function.identity())));
    }

    /**
     * Register a strategy at runtime (e.g. Python adapters loaded at startup).
     * If a strategy with the same ID already exists it will be replaced.
     */
    public void register(TradingStrategy strategy) {
        strategies.put(strategy.getId(), strategy);
    }

    /**
     * Get a strategy by its ID.
     */
    public Optional<TradingStrategy> getStrategy(String id) {
        return Optional.ofNullable(strategies.get(id));
    }

    /**
     * Get all available strategies.
     */
    public List<TradingStrategy> getAllStrategies() {
        return List.copyOf(strategies.values());
    }

    /**
     * Check if a strategy exists.
     */
    public boolean hasStrategy(String id) {
        return strategies.containsKey(id);
    }
}
