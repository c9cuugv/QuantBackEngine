package com.quantbackengine.backend.dto;

import java.util.Map;

/**
 * Result DTO for quantstats analytics.
 *
 * <p>success=false indicates the bridge was unavailable or the script failed.
 * In that case data may be null or empty — callers must check success first.</p>
 */
public record QuantstatsResult(
        boolean success,
        String action,
        Map<String, Object> data
) {}
