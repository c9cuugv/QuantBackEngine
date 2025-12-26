package com.quantbackengine.quantbackengine.report;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.backtest.Backtester;
import com.quantbackengine.quantbackengine.metrics.PerformanceMetrics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates console reports and visual charts from backtest results.
 */
public class Reporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Prints a complete console report including metrics and trade log.
     */
    /**
     * Prints a complete console report including metrics and trade log.
     */
    public void printConsoleReport(BacktestResult result, PerformanceMetrics.MetricsReport metrics) {
        System.out.println(generateReportString(result, metrics));
    }

    /**
     * Generates the report content as a string.
     */
    public String generateReportString(BacktestResult result, PerformanceMetrics.MetricsReport metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== BACKTEST REPORT ===\n");
        sb.append(metrics).append("\n");
        sb.append("Trade Log\n");
        sb.append("=========\n");

        List<Backtester.Trade> trades = result.completedTrades();
        if (trades.isEmpty()) {
            sb.append("No completed trades.\n");
        } else {
            sb.append(String.format("%-12s %-10s %-10s %-10s %-8s %-12s%n",
                    "Entry Date", "Entry $", "Exit Date", "Exit $", "Shares", "Commission $"));
            sb.append("------------------------------------------------------------------------\n");

            for (Backtester.Trade trade : trades) {
                String entryDate = trade.entryDate().toLocalDate().format(DATE_FORMAT);
                String exitDate = trade.exitDate().toLocalDate().format(DATE_FORMAT);
                sb.append(String.format("%-12s %-10.2f %-10s %-10.2f %-8.2f %-12.2f%n",
                        entryDate,
                        trade.entryPrice().doubleValue(),
                        exitDate,
                        trade.exitPrice().doubleValue(),
                        trade.shares().doubleValue(),
                        trade.commission()));
            }
        }
        sb.append("========================\n\n");
        return sb.toString();
    }

    /**
     * Saves the text report to a file.
     */
    public void saveReportToFile(String reportContent, String filePath) throws Exception {
        File outputFile = new File(filePath);
        outputFile.getParentFile().mkdirs(); // Ensure directory exists

        java.nio.file.Files.writeString(outputFile.toPath(), reportContent);
        System.out.println("Report saved to: " + outputFile.getAbsolutePath());
    }

    /**
     * Saves the equity curve as a PNG file.
     *
     * @param result   the backtest result
     * @param filePath path to save the chart (e.g., "reports/equity_curve.png")
     */
    public void saveEquityCurveChart(BacktestResult result, String filePath) throws Exception {
        JFreeChart chart = createEquityCurveChart(result);

        File outputFile = new File(filePath);
        outputFile.getParentFile().mkdirs(); // Ensure directory exists

        ChartUtils.saveChartAsPNG(outputFile, chart, 1200, 600);
        System.out.println("Equity curve chart saved to: " + outputFile.getAbsolutePath());
    }

    /**
     * Returns the Equity Curve Chart object (for Swing UI).
     */
    public JFreeChart getEquityCurveChart(BacktestResult result) {
        return createEquityCurveChart(result);
    }

    private JFreeChart createEquityCurveChart(BacktestResult result) {
        XYSeries series = new XYSeries("Portfolio Equity");

        for (Backtester.EquityPoint point : result.equityCurve()) {
            double timestamp = point.dateTime().toInstant().toEpochMilli();
            series.add(timestamp, point.portfolioValue());
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);

        return ChartFactory.createXYLineChart(
                "Equity Curve",
                "Date",
                "Portfolio Value ($)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);
    }
}