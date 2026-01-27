package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for all available trading strategies.
 * Auto-discovers strategies via Spring dependency injection.
 */
@Service
public class StrategyRegistry {

    private final Map<String, TradingStrategy> strategies;

    public StrategyRegistry(List<TradingStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        TradingStrategy::getId,
                        Function.identity()));
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
