package com.quantbackengine.backend.dto;

import java.util.Map;

/**
 * Request DTO for quantstats analytics.
 *
 * <p>action: one of "stats" | "returns" | "drawdown" | "rolling" | "montecarlo" | "full_report"</p>
 */
public record QuantstatsRequest(
        Map<String, Double> tickersWeights,
        String benchmark,
        String period,
        double riskFreeRate,
        String action
) {}
