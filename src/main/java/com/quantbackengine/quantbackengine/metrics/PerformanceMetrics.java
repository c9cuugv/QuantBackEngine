package com.quantbackengine.quantbackengine.metrics;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.backtest.Backtester.EquityPoint;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Calculates key performance metrics from backtest results.
 */
public class PerformanceMetrics {

    private final double riskFreeRate; // annual risk-free rate (e.g., 0.02 for 2%)

    public PerformanceMetrics(double riskFreeRate) {
        this.riskFreeRate = riskFreeRate;
    }

    public PerformanceMetrics() {
        this(0.0); // default: no risk-free adjustment
    }

    /**
     * Computes all metrics and returns a formatted report.
     */
    public MetricsReport calculate(BacktestResult result) {
        List<EquityPoint> equityCurve = result.equityCurve();
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException("Equity curve cannot be empty");
        }

        double initialCapital = result.initialCapital();
        double finalValue = equityCurve.get(equityCurve.size() - 1).portfolioValue();

        double totalReturn = (finalValue - initialCapital) / initialCapital;

        double years = calculateYears(equityCurve);
        double annualizedReturn = years > 0 ? Math.pow(1 + totalReturn, 1 / years) - 1 : 0;

        MaxDrawdown maxDrawdown = calculateMaxDrawdown(equityCurve);

        double sharpeRatio = calculateSharpeRatio(equityCurve, years);

        return new MetricsReport(totalReturn, annualizedReturn, maxDrawdown, sharpeRatio, years);
    }

    private double calculateYears(List<EquityPoint> equityCurve) {
        ZonedDateTime start = equityCurve.get(0).dateTime();
        ZonedDateTime end = equityCurve.get(equityCurve.size() - 1).dateTime();
        return Duration.between(start, end).toDays() / 365.25;
    }

    private MaxDrawdown calculateMaxDrawdown(List<EquityPoint> equityCurve) {
        double peak = -Double.MAX_VALUE;
        double maxDd = 0.0;
        double maxDdPct = 0.0;
        double trough = 0.0;

        for (EquityPoint point : equityCurve) {
            double value = point.portfolioValue();
            if (value > peak) {
                peak = value;
            }
            double drawdown = peak - value;
            if (drawdown > maxDd) {
                maxDd = drawdown;
                maxDdPct = maxDd / peak;
                trough = value;
            }
        }
        return new MaxDrawdown(maxDd, maxDdPct);
    }

    private double calculateSharpeRatio(List<EquityPoint> equityCurve, double years) {
        if (years <= 0 || equityCurve.size() < 2) {
            return 0.0;
        }

        int n = equityCurve.size() - 1;
        double[] returns = new double[n];

        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = equityCurve.get(i - 1).portfolioValue();
            double curr = equityCurve.get(i).portfolioValue();
            returns[i - 1] = (curr - prev) / prev;
        }

        double meanReturn = 0;
        for (double r : returns) {
            meanReturn += r;
        }
        meanReturn /= n;

        double variance = 0;
        for (double r : returns) {
            variance += Math.pow(r - meanReturn, 2);
        }
        double stdDev = n > 1 ? Math.sqrt(variance / (n - 1)) : 0;

        if (stdDev == 0) {
            return 0.0;
        }

        // Annualize assuming daily returns
        double annualizedMean = meanReturn * 252;
        double annualizedStdDev = stdDev * Math.sqrt(252);

        return (annualizedMean - riskFreeRate) / annualizedStdDev;
    }

    /**
     * Immutable record for maximum drawdown results.
     */
    public record MaxDrawdown(double absolute, double percentage) {}

    /**
     * Immutable record containing all calculated metrics.
     */
    public record MetricsReport(
            double totalReturn,
            double annualizedReturn,
            MaxDrawdown maxDrawdown,
            double sharpeRatio,
            double backtestYears) {

        @Override
        public String toString() {
            return String.format(
                    """
                    Performance Metrics
                    ===================
                    Backtest Period     : %.2f years
                    Total Return        : %.2f%%
                    Annualized Return   : %.2f%%
                    Maximum Drawdown    : $%.2f (%.2f%%)
                    Sharpe Ratio        : %.3f
                    """,
                    backtestYears,
                    totalReturn * 100,
                    annualizedReturn * 100,
                    maxDrawdown.absolute(),
                    maxDrawdown.percentage() * 100,
                    sharpeRatio
            );
        }
    }
}