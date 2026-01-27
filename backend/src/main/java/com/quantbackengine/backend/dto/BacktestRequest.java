package com.quantbackengine.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * Request DTO for running a backtest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Strategy name is required")
    private String strategy;

    private Map<String, Object> parameters;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Positive(message = "Initial capital must be positive")
    private Double initialCapital;

    private Double commissionRate;
}
