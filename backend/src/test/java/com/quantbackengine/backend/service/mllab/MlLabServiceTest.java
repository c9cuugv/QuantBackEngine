package com.quantbackengine.backend.service.mllab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.MlLabProperties;
import com.quantbackengine.backend.domain.MlLabRun;
import com.quantbackengine.backend.exception.MlLabConflictException;
import com.quantbackengine.backend.exception.MlLabDisabledException;
import com.quantbackengine.backend.repository.MarketDataRepository;
import com.quantbackengine.backend.repository.MlLabRunRepository;
import com.quantbackengine.backend.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ML Lab orchestration: queueing, one-at-a-time guard, universe validation,
 * feature gate, and process-exit handling (DONE with result / FAILED).
 */
@DataJpaTest
class MlLabServiceTest {

    private static final List<String> UNIVERSE =
            List.of("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL");
    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 1, 1);

    @TempDir
    static Path workDir;

    @Autowired
    private MlLabRunRepository runRepository;
    @Autowired
    private MarketDataRepository marketDataRepository;

    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final MlLabProcessLauncher launcher = mock(MlLabProcessLauncher.class);

    private MlLabService service(boolean enabled, Executor executor) {
        MlLabProperties props = new MlLabProperties(
                enabled, "python3", "/qlib/scripts", workDir.toString());
        return new MlLabService(runRepository, marketDataService, marketDataRepository,
                launcher, props, new ObjectMapper(), executor);
    }

    private static final Executor NOOP = task -> { /* hold task, never run */ };

    @Test
    void submitCreatesQueuedRunWithoutLaunching() throws Exception {
        MlLabRun run = service(true, NOOP).submit(UNIVERSE, START, END);

        assertEquals(MlLabRun.Status.QUEUED, run.getStatus());
        assertTrue(runRepository.findById(run.getId()).isPresent());
        verify(launcher, never()).launch(any(), any());
    }

    @Test
    void submitWhileActiveThrowsConflict() {
        MlLabService svc = service(true, NOOP);
        svc.submit(UNIVERSE, START, END);

        assertThrows(MlLabConflictException.class, () -> svc.submit(UNIVERSE, START, END));
    }

    @Test
    void submitRejectsUniverseSmallerThanFive() {
        MlLabService svc = service(true, NOOP);

        assertThrows(IllegalArgumentException.class,
                () -> svc.submit(List.of("AAPL", "MSFT", "NVDA"), START, END));
    }

    @Test
    void submitWhenDisabledThrows() {
        assertThrows(MlLabDisabledException.class,
                () -> service(false, NOOP).submit(UNIVERSE, START, END));
    }

    @Test
    void happyPathRunsToDoneWithResult() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(0);
        when(launcher.launch(any(), any())).thenAnswer(inv -> {
            Path runDir = inv.getArgument(0);
            Files.writeString(runDir.resolve("result.json"),
                    "{\"metrics\":{\"annualizedReturn\":0.12}}");
            return process;
        });

        MlLabRun run = service(true, Runnable::run).submit(UNIVERSE, START, END);

        MlLabRun done = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(MlLabRun.Status.DONE, done.getStatus());
        assertTrue(done.getResultJson().contains("annualizedReturn"));
        // CSVs exported for every symbol in the universe
        for (String s : UNIVERSE) {
            assertTrue(Files.exists(workDir.resolve(run.getId()).resolve("csv").resolve(s + ".csv")));
        }
    }

    @Test
    void nonZeroExitMarksRunFailed() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(1);
        when(launcher.launch(any(), any())).thenReturn(process);

        MlLabRun run = service(true, Runnable::run).submit(UNIVERSE, START, END);

        MlLabRun failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(MlLabRun.Status.FAILED, failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("code 1"));
    }
}
