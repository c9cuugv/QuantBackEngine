package com.quantbackengine.backend.service.mllab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.MlLabProperties;
import com.quantbackengine.backend.domain.MarketData;
import com.quantbackengine.backend.domain.MlLabRun;
import com.quantbackengine.backend.exception.MlLabConflictException;
import com.quantbackengine.backend.exception.MlLabDisabledException;
import com.quantbackengine.backend.repository.MarketDataRepository;
import com.quantbackengine.backend.repository.MlLabRunRepository;
import com.quantbackengine.backend.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * ML Lab orchestration: validates the universe, persists the run, prepares
 * per-symbol CSVs from the DB cache, launches the detached qlib runner and
 * records its outcome. One run at a time by design.
 */
@Service
@Slf4j
public class MlLabService {

    private static final EnumSet<MlLabRun.Status> ACTIVE =
            EnumSet.of(MlLabRun.Status.QUEUED, MlLabRun.Status.RUNNING);

    private final MlLabRunRepository runRepository;
    private final MarketDataService marketDataService;
    private final MarketDataRepository marketDataRepository;
    private final MlLabProcessLauncher launcher;
    private final MlLabProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public MlLabService(MlLabRunRepository runRepository,
                        MarketDataService marketDataService,
                        MarketDataRepository marketDataRepository,
                        MlLabProcessLauncher launcher,
                        MlLabProperties properties,
                        ObjectMapper objectMapper,
                        @Qualifier("mllabExecutor") Executor executor) {
        this.runRepository = runRepository;
        this.marketDataService = marketDataService;
        this.marketDataRepository = marketDataRepository;
        this.launcher = launcher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public synchronized MlLabRun submit(List<String> symbols, LocalDate start, LocalDate end) {
        requireEnabled();

        List<String> universe = sanitizeUniverse(symbols);
        if (universe.size() < 5 || universe.size() > 50) {
            throw new IllegalArgumentException(
                    "Universe must contain 5-50 distinct symbols, got " + universe.size());
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startDate must be before endDate");
        }
        if (runRepository.existsByStatusIn(ACTIVE)) {
            throw new MlLabConflictException();
        }

        MlLabRun run = runRepository.save(MlLabRun.builder()
                .status(MlLabRun.Status.QUEUED)
                .paramsJson(writeJson(Map.of(
                        "symbols", universe,
                        "startDate", start.toString(),
                        "endDate", end.toString())))
                .createdAt(LocalDateTime.now())
                .build());

        executor.execute(() -> executeRun(run.getId(), universe, start, end));
        return run;
    }

    public MlLabRun getRun(String id) {
        requireEnabled();
        return runRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown ML Lab run: " + id));
    }

    public List<MlLabRun> listRuns() {
        requireEnabled();
        return runRepository.findAllByOrderByCreatedAtDesc();
    }

    // -------------------------------------------------------------------
    // Async pipeline
    // -------------------------------------------------------------------

    void executeRun(String runId, List<String> symbols, LocalDate start, LocalDate end) {
        MlLabRun run = runRepository.findById(runId).orElseThrow();
        run.setStatus(MlLabRun.Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepository.save(run);

        try {
            Path runDir = runDir(runId);
            Path csvDir = runDir.resolve("csv");
            Files.createDirectories(csvDir);

            for (String symbol : symbols) {
                marketDataService.getBarSeries(symbol, start, end);
                exportCsv(symbol, start, end, csvDir);
            }

            Path paramsFile = runDir.resolve("params.json");
            Files.writeString(paramsFile, writeJson(Map.of(
                    "run_id", runId,
                    "csv_dir", csvDir.toAbsolutePath().toString(),
                    "qlib_dir", runDir.resolve("qlib_data").toAbsolutePath().toString(),
                    "out_file", runDir.resolve("result.json").toAbsolutePath().toString(),
                    "qlib_scripts", properties.qlibScriptsPath(),
                    "symbols", symbols,
                    "start", start.toString(),
                    "end", end.toString())));

            Process process = launcher.launch(runDir, paramsFile);
            int exitCode = process.waitFor();
            handleProcessExit(runId, exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(runId, "Run interrupted");
        } catch (Exception e) {
            log.error("ML Lab run {} failed during preparation: {}", runId, e.getMessage());
            fail(runId, e.getMessage());
        }
    }

    void handleProcessExit(String runId, int exitCode) {
        MlLabRun run = runRepository.findById(runId).orElseThrow();
        Path resultFile = runDir(runId).resolve("result.json");

        if (exitCode == 0 && Files.exists(resultFile)) {
            try {
                run.setResultJson(Files.readString(resultFile));
                run.setStatus(MlLabRun.Status.DONE);
                run.setFinishedAt(LocalDateTime.now());
                runRepository.save(run);
                log.info("ML Lab run {} DONE", runId);
                return;
            } catch (IOException e) {
                fail(runId, "Could not read result file: " + e.getMessage());
                return;
            }
        }
        fail(runId, "Runner exited with code " + exitCode
                + (Files.exists(resultFile) ? "" : " (no result file)"));
    }

    private void fail(String runId, String message) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(MlLabRun.Status.FAILED);
            run.setErrorMessage(message);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
        });
        log.warn("ML Lab run {} FAILED: {}", runId, message);
    }

    private void exportCsv(String symbol, LocalDate start, LocalDate end, Path csvDir) throws IOException {
        List<MarketData> rows = marketDataRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                symbol, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        StringBuilder sb = new StringBuilder("date,open,high,low,close,volume\n");
        for (MarketData m : rows) {
            sb.append(m.getTimestamp().toLocalDate()).append(',')
              .append(m.getOpen().toPlainString()).append(',')
              .append(m.getHigh().toPlainString()).append(',')
              .append(m.getLow().toPlainString()).append(',')
              .append(m.getClose().toPlainString()).append(',')
              .append(m.getVolume()).append('\n');
        }
        Files.writeString(csvDir.resolve(symbol + ".csv"), sb.toString());
    }

    private Path runDir(String runId) {
        return Path.of(properties.workDir()).resolve(runId);
    }

    private static List<String> sanitizeUniverse(List<String> symbols) {
        Set<String> clean = new LinkedHashSet<>();
        if (symbols != null) {
            for (String s : symbols) {
                if (s == null) continue;
                String t = s.toUpperCase().replaceAll("[^A-Z0-9\\-]", "");
                if (!t.isEmpty()) clean.add(t);
            }
        }
        return new ArrayList<>(clean);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new MlLabDisabledException();
        }
    }
}
