package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.strategy.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

    @Mock
    private StrategyRegistry strategyRegistry;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private BacktestService backtestService;

    @Test
    void runBacktest_ShouldThrowException_WhenStartDateAfterEndDate() {
        BacktestRequest request = BacktestRequest.builder()
                .symbol("AAPL")
                .strategy("SMA_CROSSOVER")
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2022, 1, 1))
                .build();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            backtestService.runBacktest(request);
        });

        assertTrue(exception.getMessage().contains("Start date must be before end date"));
    }
}
