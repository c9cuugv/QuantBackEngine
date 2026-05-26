package com.quantbackengine.backend.dto;

/**
 * Describes a Python-backed market data source.
 *
 * @param id          machine-readable identifier (e.g. {@code "yfinance"})
 * @param displayName human-readable label shown in the UI
 * @param scriptPath  path to the data-fetch script, relative to the scripts base path
 * @param available   {@code true} when the Python bridge is reachable and the script exists
 */
public record DataSourceInfo(
        String id,
        String displayName,
        String scriptPath,
        boolean available
) {}
