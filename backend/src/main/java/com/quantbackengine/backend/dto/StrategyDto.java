package com.quantbackengine.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO representing an available trading strategy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyDto {

    private String id;
    private String name;
    private String description;
    private List<ParameterDto> parameters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDto {
        private String name;
        private String type; // INTEGER, DOUBLE, STRING
        private Object defaultValue;
        private Object minValue;
        private Object maxValue;
        private String description;
    }
}
