package com.quantbackengine.backend.strategy;

import java.util.Map;

/**
 * Utility helpers for safely extracting typed parameters from a Map<String, Object>.
 */
final class StrategyParamUtils {
    private StrategyParamUtils() {}

    static int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    static double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
