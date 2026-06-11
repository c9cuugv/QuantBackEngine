package com.quantbackengine.backend.service.mllab;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Seam for spawning the detached qlib runner process. Mockable in tests.
 */
public interface MlLabProcessLauncher {

    /**
     * Launch the qlib runner for the given run directory.
     *
     * @param runDir     working directory for this run (CSVs, params, output)
     * @param paramsFile JSON file with runner parameters
     * @return the spawned process
     */
    Process launch(Path runDir, Path paramsFile) throws IOException;
}
