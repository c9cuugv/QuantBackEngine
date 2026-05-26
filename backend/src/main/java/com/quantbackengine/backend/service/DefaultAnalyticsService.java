package com.quantbackengine.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of AnalyticsService.
 * Delegates to the Python bridge to run quantstats_analytics.py.
 * Never throws — all failures are captured as QuantstatsResult(success=false).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAnalyticsService implements AnalyticsService {

    private final PythonBridgeService bridge;
    private final ObjectMapper objectMapper;

    @Override
    public QuantstatsResult runQuantstats(QuantstatsRequest request) {
        String action = request != null ? request.action() : null;
        try {
            if (!bridge.isAvailable()) {
                log.warn("Python bridge unavailable — skipping quantstats analytics");
                return new QuantstatsResult(false, action, Map.of());
            }

            List<String> args = buildArgs(request);
            JsonNode result = bridge.invoke("Analytics/quantstats_analytics.py", args);

            Map<String, Object> data = parseData(result);
            return new QuantstatsResult(true, action, data);

        } catch (Exception e) {
            log.warn("quantstats analytics failed: {}", e.getMessage());
            return new QuantstatsResult(false, action, Map.of());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> buildArgs(QuantstatsRequest request) {
        List<String> args = new ArrayList<>();
        if (request == null) return args;

        if (request.action() != null) {
            args.add("--action");
            args.add(request.action());
        }
        if (request.benchmark() != null) {
            args.add("--benchmark");
            args.add(request.benchmark());
        }
        if (request.period() != null) {
            args.add("--period");
            args.add(request.period());
        }
        args.add("--risk-free-rate");
        args.add(String.valueOf(request.riskFreeRate()));

        if (request.tickersWeights() != null && !request.tickersWeights().isEmpty()) {
            try {
                args.add("--tickers-weights");
                args.add(objectMapper.writeValueAsString(request.tickersWeights()));
            } catch (Exception e) {
                log.warn("Failed to serialize tickersWeights: {}", e.getMessage());
            }
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(JsonNode result) {
        try {
            return objectMapper.convertValue(result, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse quantstats result as map: {}", e.getMessage());
            Map<String, Object> raw = new HashMap<>();
            raw.put("raw", result.toString());
            return raw;
        }
    }
}
