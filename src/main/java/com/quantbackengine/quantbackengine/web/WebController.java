package com.quantbackengine.quantbackengine.web;

import com.quantbackengine.quantbackengine.service.BacktestService;
import com.quantbackengine.quantbackengine.service.StrategyOptimizer;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow requests from any origin
public class WebController {

    private final BacktestService backtestService = new BacktestService();
    private final StrategyOptimizer optimizer = new StrategyOptimizer();

    /**
     * Run backtest with uploaded CSV and parameters
     */
    @PostMapping("/backtest")
    public Map<String, Object> runBacktest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("shortSma") int shortSma,
            @RequestParam("longSma") int longSma) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("backtest_", ".csv");
            file.transferTo(tempFile.toFile());

            // Run backtest
            BacktestService.RunResult result = backtestService.runBacktest(
                    tempFile.toString(), shortSma, longSma);

            // Prepare response
            response.put("success", true);
            response.put("report", result.reportString());
            response.put("metrics", Map.of(
                    "totalReturn", result.metrics().totalReturn() * 100,
                    "annualizedReturn", result.metrics().annualizedReturn() * 100,
                    "sharpeRatio", result.metrics().sharpeRatio(),
                    "sortinoRatio", result.metrics().sortinoRatio(),
                    "winRate", result.metrics().winRate() * 100,
                    "profitFactor", result.metrics().profitFactor(),
                    "maxDrawdown", result.metrics().maxDrawdown().percentage() * 100));

            // Extract equity curve data for chart
            var equityCurve = result.backtestResult().equityCurve();
            response.put("chartData", equityCurve.stream()
                    .map(point -> Map.of(
                            "date", point.dateTime().toString(),
                            "value", point.portfolioValue()))
                    .toList());

            // Clean up temp file
            tempFile.toFile().delete();

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * Optimize SMA parameters
     */
    @PostMapping("/optimize")
    public Map<String, Object> optimize(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("optimize_", ".csv");
            file.transferTo(tempFile.toFile());

            // Run optimization
            List<StrategyOptimizer.OptimizationResult> results = optimizer.optimizeSma(tempFile.toString(), 5, 50, 60,
                    200);

            // Return top 10 results
            response.put("success", true);
            response.put("results", results.stream()
                    .limit(10)
                    .map(r -> Map.of(
                            "shortSma", r.shortSma(),
                            "longSma", r.longSma(),
                            "totalReturn", r.totalReturn() * 100,
                            "sharpeRatio", r.sharpeRatio(),
                            "winRate", r.winRate() * 100,
                            "maxDrawdown", r.maxDrawdown() * 100))
                    .toList());

            // Clean up temp file
            tempFile.toFile().delete();

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}
