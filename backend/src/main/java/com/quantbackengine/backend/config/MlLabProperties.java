package com.quantbackengine.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ML Lab (qlib pipeline) configuration. Disabled on deployments without a
 * host Python environment capable of running qlib (e.g. the VPS).
 */
@ConfigurationProperties(prefix = "fincept.python.mllab")
public record MlLabProperties(
        boolean enabled,
        String pythonExecutable,
        String qlibScriptsPath,
        String workDir
) {
}
