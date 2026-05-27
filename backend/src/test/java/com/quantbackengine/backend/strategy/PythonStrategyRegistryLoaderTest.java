package com.quantbackengine.backend.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PythonStrategyRegistryLoaderTest {

    @TempDir
    Path tempDir;

    @Mock
    private StrategyRegistry strategyRegistry;

    @Mock
    private PythonBridgeService bridge;

    private ObjectMapper objectMapper;
    private PythonStrategyRegistryLoader loader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        PythonBridgeProperties properties = new PythonBridgeProperties(
                new PythonBridgeProperties.Scripts(tempDir.toString()),
                new PythonBridgeProperties.Python("python3", 60,
                        new PythonBridgeProperties.Python.MarketData(true))
        );
        loader = new PythonStrategyRegistryLoader(properties, strategyRegistry, objectMapper, bridge);
    }

    @Test
    void happyPath_validRegistryJson_registersAdapters() throws Exception {
        // Arrange
        String json = """
                [
                  {"id": "my_strat", "scriptPath": "algo_trading/my_strat.py"},
                  {"id": "another_strat", "scriptPath": "algo_trading/another.py"}
                ]
                """;
        Files.writeString(tempDir.resolve("registry_index.json"), json);

        // Act
        loader.run(new DefaultApplicationArguments());

        // Assert
        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRegistry, times(2)).register(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TradingStrategy::getId)
                .containsExactlyInAnyOrder("fct:my_strat", "fct:another_strat");
    }

    @Test
    void singleEntry_idMatchesFctPrefix() throws Exception {
        // Arrange
        String json = """
                [{"id": "my_strat", "scriptPath": "algo_trading/my_strat.py"}]
                """;
        Files.writeString(tempDir.resolve("registry_index.json"), json);

        // Act
        loader.run(new DefaultApplicationArguments());

        // Assert
        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRegistry, times(1)).register(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("fct:my_strat");
    }

    @Test
    void missingFile_noRegistrationNoException() throws Exception {
        // registry_index.json does NOT exist in tempDir

        // Act — must not throw
        loader.run(new DefaultApplicationArguments());

        // Assert
        verify(strategyRegistry, never()).register(any());
    }

    @Test
    void malformedJson_noRegistrationNoException() throws Exception {
        // Arrange
        Files.writeString(tempDir.resolve("registry_index.json"), "not json {{{");

        // Act — must not throw
        loader.run(new DefaultApplicationArguments());

        // Assert
        verify(strategyRegistry, never()).register(any());
    }

    @Test
    void nonArrayJson_noRegistrationNoException() throws Exception {
        // Arrange — valid JSON but not an array
        Files.writeString(tempDir.resolve("registry_index.json"),
                "{\"id\": \"strat\", \"scriptPath\": \"algo_trading/strat.py\"}");

        // Act — must not throw
        loader.run(new DefaultApplicationArguments());

        // Assert
        verify(strategyRegistry, never()).register(any());
    }

    @Test
    void entryMissingId_skipped() throws Exception {
        // Arrange
        String json = """
                [
                  {"scriptPath": "algo_trading/no_id.py"},
                  {"id": "valid_strat", "scriptPath": "algo_trading/valid.py"}
                ]
                """;
        Files.writeString(tempDir.resolve("registry_index.json"), json);

        // Act
        loader.run(new DefaultApplicationArguments());

        // Assert — only the valid entry is registered
        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRegistry, times(1)).register(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("fct:valid_strat");
    }
}
