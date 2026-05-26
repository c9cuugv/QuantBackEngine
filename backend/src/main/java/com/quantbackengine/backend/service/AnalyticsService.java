package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.QuantstatsRequest;
import com.quantbackengine.backend.dto.QuantstatsResult;

/**
 * Service for running Python-backed analytics (quantstats, Monte Carlo, etc.).
 * Implementations MUST never throw — failures are captured as QuantstatsResult(success=false).
 */
public interface AnalyticsService {

    /**
     * Run quantstats analytics for the given request.
     *
     * <p>This method NEVER throws. Any failure (bridge unavailable, script error,
     * JSON parse error) is returned as {@code QuantstatsResult(success=false, ...)}.</p>
     *
     * @param request the analytics request
     * @return a QuantstatsResult — always non-null, never throws
     */
    QuantstatsResult runQuantstats(QuantstatsRequest request);
}
