package com.quantbackengine.backend.service.mllab;

import com.quantbackengine.backend.config.MlLabProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Spawns the qlib runner via the configured Python executable. Stdout/stderr
 * go to {@code runner.log} inside the run directory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultMlLabProcessLauncher implements MlLabProcessLauncher {

    private static final String RUNNER_SCRIPT = "scripts/mllab/qlib_runner.py";

    private final MlLabProperties properties;

    @Override
    public Process launch(Path runDir, Path paramsFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                properties.pythonExecutable(),
                RUNNER_SCRIPT,
                "--params", paramsFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(runDir.resolve("runner.log").toFile());
        log.info("Launching qlib runner: {} {}", properties.pythonExecutable(), paramsFile);
        return pb.start();
    }
}
