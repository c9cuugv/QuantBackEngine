package com.quantbackengine.backend.repository;

import com.quantbackengine.backend.domain.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence for cached OHLCV bars keyed by (symbol, timestamp).
 */
public interface MarketDataRepository extends JpaRepository<MarketData, String> {

    List<MarketData> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol, LocalDateTime from, LocalDateTime to);

    @Query("select distinct m.symbol from MarketData m order by m.symbol")
    List<String> findDistinctSymbols();
}
