package com.quantbackengine.quantbackengine.service;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.backtest.Backtester;
import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.data.DataProvider;
import com.quantbackengine.quantbackengine.metrics.PerformanceMetrics;
import com.quantbackengine.quantbackengine.report.Reporter;
import com.quantbackengine.quantbackengine.strategy.MovingAverageCrossoverStrategy;
import com.quantbackengine.quantbackengine.strategy.TradingStrategy;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;

/**
 * Service class to encapsulate backtest execution logic.
 * Makes backtesting reusable from UI and other components.
 */
public class BacktestService {

    public record RunResult(
            BacktestResult backtestResult,
            PerformanceMetrics.MetricsReport metrics,
            String reportString) {
    }

    /**
     * Runs a backtest with the given parameters.
     *
     * @param csvPath  Path to CSV file with historical data
     * @param shortSma Short SMA period
     * @param longSma  Long SMA period
     * @return RunResult containing backtest results, metrics, and formatted report
     */
    public RunResult runBacktest(String csvPath, int shortSma, int longSma) throws Exception {
        // Configuration
        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.now();
        double initialCapital = 100_000.0;
        double commissionRate = 0.001;

        // 1. Load data
        DataProvider dataProvider = new CsvDataProvider(csvPath);
        String symbol = extractSymbolFromPath(csvPath);
        BarSeries series = dataProvider.getHistoricalData(symbol, start, end);

        // 2. Define strategy
        TradingStrategy strategy = new MovingAverageCrossoverStrategy(shortSma, longSma);

        // 3. Run backtest
        Backtester backtester = new Backtester(initialCapital, commissionRate);
        BacktestResult result = backtester.run(series, strategy);

        // 4. Calculate metrics
        PerformanceMetrics metricsCalculator = new PerformanceMetrics(0.02);
        PerformanceMetrics.MetricsReport metrics = metricsCalculator.calculate(result);

        // 5. Generate report string
        Reporter reporter = new Reporter();
        String reportString = reporter.generateReportString(result, metrics);

        return new RunResult(result, metrics, reportString);
    }

    private String extractSymbolFromPath(String csvPath) {
        if (csvPath.contains("AAPL"))
            return "AAPL";
        if (csvPath.contains("MSFT"))
            return "MSFT";
        if (csvPath.contains("GOOGL"))
            return "GOOGL";
        // Extract filename without extension as fallback
        String filename = csvPath.substring(csvPath.lastIndexOf("\\") + 1);
        return filename.replace(".csv", "");
    }
}
