package com.quantbackengine.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Single-thread executor enforces the one-ML-Lab-run-at-a-time model.
 */
@Configuration
public class MlLabConfig {

    @Bean(name = "mllabExecutor")
    public Executor mllabExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mllab-runner");
            t.setDaemon(true);
            return t;
        });
    }
}
