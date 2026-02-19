package com.quantbackengine.backend.util;

import com.quantbackengine.backend.dto.BacktestResponse.EquityPointDto;
import com.quantbackengine.backend.dto.BacktestResponse.MetricsDto;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for calculating backtest trading metrics.
 */
public class MetricsCalculator {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double DAYS_PER_YEAR = 365.25;

    private MetricsCalculator() {
        // Prevent instantiation
    }

    public static MetricsDto calculateMetrics(
            List<EquityPointDto> curve,
            double initialCapital,
            int totalTrades,
            int wins,
            int losses,
            double riskFreeRate) {

        if (curve == null || curve.isEmpty()) {
            return MetricsDto.builder().build();
        }

        double finalValue = curve.get(curve.size() - 1).getValue();
        double totalReturn = (finalValue - initialCapital) / initialCapital;

        // Calculate years
        long startMs = curve.get(0).getTimestamp();
        long endMs = curve.get(curve.size() - 1).getTimestamp();
        double years = (endMs - startMs) / (DAYS_PER_YEAR * 24 * 60 * 60 * 1000.0);
        double annualizedReturn = years > 0 ? Math.pow(1 + totalReturn, 1 / years) - 1 : 0;

        // Max drawdown
        double peak = -Double.MAX_VALUE;
        double maxDd = 0.0;
        double maxDdPct = 0.0;

        for (EquityPointDto point : curve) {
            double value = point.getValue();
            if (value > peak) {
                peak = value;
            }
            double drawdown = peak - value;
            if (drawdown > maxDd) {
                maxDd = drawdown;
                maxDdPct = peak != 0 ? maxDd / peak : 0; // Fix potential division by zero if peak is 0 (unlikely but
                                                         // safe)
            }
        }

        // Sharpe ratio
        double sharpe = 0.0;
        if (curve.size() > 1) {
            double[] returns = new double[curve.size() - 1];
            for (int i = 1; i < curve.size(); i++) {
                double prev = curve.get(i - 1).getValue();
                double curr = curve.get(i).getValue();
                returns[i - 1] = (curr - prev) / prev;
            }

            double mean = Arrays.stream(returns).average().orElse(0);
            double variance = Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).sum() / (returns.length - 1);
            double stdDev = Math.sqrt(variance);

            if (stdDev > 0) {
                double annualizedMean = mean * TRADING_DAYS_PER_YEAR;
                double annualizedStdDev = stdDev * Math.sqrt(TRADING_DAYS_PER_YEAR);
                sharpe = (annualizedMean - riskFreeRate) / annualizedStdDev;
            }
        }

        double winRate = totalTrades > 0 ? (double) wins / totalTrades : 0;

        return MetricsDto.builder()
                .totalReturn(totalReturn)
                .annualizedReturn(annualizedReturn)
                .maxDrawdown(maxDd)
                .maxDrawdownPercent(maxDdPct)
                .sharpeRatio(sharpe)
                .backtestYears(years)
                .totalTrades(totalTrades)
                .winningTrades(wins)
                .losingTrades(losses)
                .winRate(winRate)
                .build();
    }
}
