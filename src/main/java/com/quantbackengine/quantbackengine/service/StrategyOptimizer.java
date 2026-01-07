package com.quantbackengine.quantbackengine.service;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.backtest.Backtester;
import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.data.DataProvider;
import com.quantbackengine.quantbackengine.metrics.PerformanceMetrics;
import com.quantbackengine.quantbackengine.strategy.MovingAverageCrossoverStrategy;
import com.quantbackengine.quantbackengine.strategy.TradingStrategy;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optimizes strategy parameters by testing multiple combinations.
 */
public class StrategyOptimizer {

    public record OptimizationResult(
            int shortSma,
            int longSma,
            double totalReturn,
            double sharpeRatio,
            double winRate,
            double maxDrawdown) {
    }

    /**
     * Sweeps through SMA parameter combinations to find the best performing
     * strategy.
     *
     * @param csvPath  Path to CSV file with historical data
     * @param shortMin Minimum short SMA period
     * @param shortMax Maximum short SMA period
     * @param longMin  Minimum long SMA period
     * @param longMax  Maximum long SMA period
     * @return List of results sorted by total return (best first)
     */
    public List<OptimizationResult> optimizeSma(String csvPath, int shortMin, int shortMax,
            int longMin, int longMax) throws Exception {
        List<OptimizationResult> results = new ArrayList<>();

        // Load data once for all runs
        DataProvider dataProvider = new CsvDataProvider(csvPath);

        // Use dynamic date range - get all available data
        BarSeries series = dataProvider.getHistoricalData("OPTIMIZE",
                LocalDate.of(1900, 1, 1), LocalDate.of(2100, 12, 31));

        // Validate data
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("No data found in CSV file. Check date format and column names.");
        }

        if (series.getBarCount() < longMax) {
            throw new IllegalArgumentException(
                    "Insufficient data: CSV has " + series.getBarCount() + " days, but Long SMA needs at least "
                            + longMax + " days. " +
                            "Try using a larger dataset or reduce the Long SMA maximum.");
        }

        Backtester backtester = new Backtester(100_000.0, 0.001);
        PerformanceMetrics metricsCalculator = new PerformanceMetrics(0.02);

        int totalCombinations = 0;
        int successfulRuns = 0;

        // Sweep through parameter combinations
        for (int shortSma = shortMin; shortSma <= shortMax; shortSma += 5) {
            for (int longSma = longMin; longSma <= longMax; longSma += 10) {
                // Skip invalid combinations
                if (shortSma >= longSma)
                    continue;

                totalCombinations++;

                try {
                    TradingStrategy strategy = new MovingAverageCrossoverStrategy(shortSma, longSma);
                    BacktestResult result = backtester.run(series, strategy);
                    PerformanceMetrics.MetricsReport metrics = metricsCalculator.calculate(result);

                    results.add(new OptimizationResult(
                            shortSma,
                            longSma,
                            metrics.totalReturn(),
                            metrics.sharpeRatio(),
                            metrics.winRate(),
                            metrics.maxDrawdown().percentage()));

                    successfulRuns++;

                } catch (Exception e) {
                    // Log but continue with other combinations
                    System.err.println("Failed for SMA " + shortSma + "/" + longSma + ": " + e.getMessage());
                }
            }
        }

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "Optimization failed: No valid results from " + totalCombinations + " combinations. " +
                            "Check CSV format: needs columns 'Date,Open,High,Low,Close,Volume' with dates in YYYY-MM-DD format.");
        }

        System.out.println(
                "Optimization complete: " + successfulRuns + "/" + totalCombinations + " combinations successful");

        // Sort by total return (descending)
        results.sort(Comparator.comparingDouble(OptimizationResult::totalReturn).reversed());

        return results;
    }
}
