package com.quantbackengine.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO representing an available trading strategy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyDto {

    @NotNull(message = "ID cannot be null")
    private String id;

    @NotNull(message = "Name cannot be null")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    private String description;

    private List<ParameterDto> parameters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDto {
        @NotNull(message = "Parameter name cannot be null")
        private String name;

        @NotNull(message = "Type cannot be null")
        private String type; // INTEGER, DOUBLE, STRING
        private Object defaultValue;
        private Object minValue;
        private Object maxValue;
        private String description;
    }
}
