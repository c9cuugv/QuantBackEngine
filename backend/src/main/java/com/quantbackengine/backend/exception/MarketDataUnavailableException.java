package com.quantbackengine.backend.exception;

import java.util.List;

/**
 * Thrown when no market data source can serve a request: the Python bridge is
 * down (or returned nothing) and the DB cache has no coverage for the symbol.
 * Carries the list of symbols that ARE cached so clients can offer offline options.
 */
public class MarketDataUnavailableException extends RuntimeException {

    private final List<String> cachedSymbols;

    public MarketDataUnavailableException(String symbol, List<String> cachedSymbols) {
        super("Market data unavailable for " + symbol
                + " — data source is down and no cached bars exist for it");
        this.cachedSymbols = List.copyOf(cachedSymbols);
    }

    public List<String> getCachedSymbols() {
        return cachedSymbols;
    }
}
