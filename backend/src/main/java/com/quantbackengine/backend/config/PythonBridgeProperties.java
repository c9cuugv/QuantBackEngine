package com.quantbackengine.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the FinceptTerminal Python bridge.
 * 
 * Bound to properties prefixed with "fincept" in application.properties.
 */
@ConfigurationProperties(prefix = "fincept")
public record PythonBridgeProperties(
        Scripts scripts,
        Python python
) {
    public record Scripts(String basePath) {}
    
    public record Python(
            String executable,
            int timeoutSeconds,
            MarketData marketData
    ) {
        public record MarketData(boolean enabled) {}
    }
}
