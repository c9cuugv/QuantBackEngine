package com.quantbackengine.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads registry_index.json at startup and registers a PythonStrategyAdapter
 * for each entry into the StrategyRegistry.
 *
 * <p>If the file is missing or malformed, logs WARN and registers zero strategies
 * without failing startup.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PythonStrategyRegistryLoader implements ApplicationRunner {

    private final PythonBridgeProperties properties;
    private final StrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;
    private final PythonBridgeService bridge;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path registryFile = Paths.get(properties.scripts().basePath())
                    .resolve("registry_index.json");

            if (!Files.exists(registryFile)) {
                log.warn("registry_index.json not found, no Python strategies registered");
                return;
            }

            JsonNode root = objectMapper.readTree(registryFile.toFile());

            if (!root.isArray()) {
                log.warn("registry_index.json is not a JSON array");
                return;
            }

            int registered = 0;
            for (JsonNode entry : root) {
                String id = entry.path("id").asText(null);
                String scriptPath = entry.path("scriptPath").asText(null);

                if (id == null || id.isBlank() || scriptPath == null || scriptPath.isBlank()) {
                    log.warn("Skipping registry entry with missing id or scriptPath: {}", entry);
                    continue;
                }

                PythonStrategyAdapter adapter = new PythonStrategyAdapter(id, scriptPath, bridge, objectMapper);
                strategyRegistry.register(adapter);
                registered++;
                log.info("Registered Python strategy: fct:{} -> {}", id, scriptPath);
            }

            log.info("PythonStrategyRegistryLoader: registered {} Python strategies", registered);

        } catch (Exception e) {
            log.warn("Failed to load Python strategy registry: {}. No Python strategies registered.", e.getMessage());
        }
    }
}
