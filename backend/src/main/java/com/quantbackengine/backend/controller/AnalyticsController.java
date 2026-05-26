package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;
import com.quantbackengine.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for analytics endpoints.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * POST /api/v1/analytics/quantstats
     *
     * <p>Runs quantstats analytics via the Python bridge.
     * Always returns HTTP 200 — success/failure is indicated by {@code QuantstatsResult.success}.</p>
     */
    @PostMapping("/quantstats")
    public ResponseEntity<QuantstatsResult> runQuantstats(@RequestBody QuantstatsRequest request) {
        log.info("POST /api/v1/analytics/quantstats action={}", request != null ? request.action() : "null");
        QuantstatsResult result = analyticsService.runQuantstats(request);
        return ResponseEntity.ok(result);
    }
}
