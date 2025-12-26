package com.quantbackengine.quantbackengine.data;

import org.ta4j.core.BarSeries;
import java.time.LocalDate;

public interface DataProvider {
    /**
     * Fetches historical price data for the given symbol and date range.
     *
     * @param symbol the stock symbol (e.g., "AAPL")
     * @param start the start date of the data range
     * @param end the end date of the data range
     * @return a BarSeries containing the historical data
     * @throws Exception if data fetching fails
     */
    BarSeries getHistoricalData(String symbol, LocalDate start, LocalDate end) throws Exception;
}