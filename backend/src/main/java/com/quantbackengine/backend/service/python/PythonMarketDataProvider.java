package com.quantbackengine.backend.service.python;

import com.quantbackengine.backend.dto.DataSourceInfo;
import com.quantbackengine.backend.dto.OhlcvBar;

import java.time.LocalDate;
import java.util.List;

/**
 * Provides market data by invoking Python data-fetch scripts via the Python bridge.
 */
public interface PythonMarketDataProvider {

    /**
     * Fetch historical OHLCV bars for a symbol from the given source.
     *
     * <p>Returned bars are sorted ascending by timestamp and satisfy the OHLCV invariant:
     * {@code high >= max(open, close)}, {@code low <= min(open, close)}, {@code volume >= 0}.
     *
     * @param symbol ticker symbol
     * @param start  inclusive start date
     * @param end    inclusive end date
     * @param source data source identifier (e.g. {@code "yfinance"})
     * @return sorted, validated list of OHLCV bars (may be empty)
     */
    List<OhlcvBar> fetchHistorical(String symbol, LocalDate start, LocalDate end, String source);

    /**
     * List all known data sources with their availability status.
     *
     * @return list of {@link DataSourceInfo} for each registered source
     */
    List<DataSourceInfo> listSources();
}
