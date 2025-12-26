package com.quantbackengine.quantbackengine;

import com.quantbackengine.quantbackengine.backtest.Backtester;
import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.data.DataProvider;
import com.quantbackengine.quantbackengine.metrics.PerformanceMetrics;
import com.quantbackengine.quantbackengine.report.Reporter;
import com.quantbackengine.quantbackengine.strategy.MovingAverageCrossoverStrategy;
import com.quantbackengine.quantbackengine.strategy.TradingStrategy;
import com.quantbackengine.quantbackengine.ui.Dashboard;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args) throws Exception {
        // Configuration
        String symbol = "AAPL";
        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 24);
        double initialCapital = 100_000.0;
        double commissionRate = 0.001; // 0.1%

        // 1. Load data
        DataProvider dataProvider = new CsvDataProvider("data/AAPL.csv");
        BarSeries series = dataProvider.getHistoricalData(symbol, start, end);
        System.out.println("Loaded " + series.getBarCount() + " bars for " + symbol);

        // 2. Define strategy
        TradingStrategy strategy = new MovingAverageCrossoverStrategy(50, 200);

        // 3. Run backtest
        Backtester backtester = new Backtester(initialCapital, commissionRate);
        BacktestResult result = backtester.run(series, strategy);

        // 4. Calculate metrics
        PerformanceMetrics metricsCalculator = new PerformanceMetrics(0.02); // 2% risk-free rate
        PerformanceMetrics.MetricsReport metrics = metricsCalculator.calculate(result);

        // 5. Generate report
        Reporter reporter = new Reporter();
        String reportString = reporter.generateReportString(result, metrics);

        // Print to console
        System.out.println(reportString);

        // Save to file (e.g., reports/report_YYYYMMDD_HHMMSS.txt)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportPath = "reports/report_" + timestamp + ".txt";
        reporter.saveReportToFile(reportString, reportPath);

        // Save chart image
        reporter.saveEquityCurveChart(result, "reports/equity_curve_" + timestamp + ".png");

        // 6. Show Dashboard
        javax.swing.SwingUtilities.invokeLater(() -> {
            Dashboard dashboard = new Dashboard(reporter);
            dashboard.show(result, reportString);
        });
    }
}