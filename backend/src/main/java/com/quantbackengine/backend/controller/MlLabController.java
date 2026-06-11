package com.quantbackengine.backend.controller;

import com.quantbackengine.backend.domain.MlLabRun;
import com.quantbackengine.backend.service.mllab.MlLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Async ML Lab API: submit a qlib pipeline run, poll status, fetch results.
 */
@RestController
@RequestMapping("/api/v1/mllab")
@RequiredArgsConstructor
@Tag(name = "ML Lab", description = "Qlib ML pipeline runs")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class MlLabController {

    private final MlLabService mlLabService;

    public record MlLabRunRequest(
            @NotEmpty List<String> symbols,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    }

    @GetMapping("/config")
    @Operation(summary = "ML Lab availability", description = "Whether ML Lab is enabled on this deployment")
    public Map<String, Object> config() {
        return Map.of("enabled", mlLabService.isEnabled());
    }

    @PostMapping("/runs")
    @Operation(summary = "Submit ML Lab run", description = "Start an async qlib train/predict/backtest run. 409 if one is active.")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody MlLabRunRequest request) {
        MlLabRun run = mlLabService.submit(request.symbols(), request.startDate(), request.endDate());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(statusBody(run));
    }

    @GetMapping("/runs")
    @Operation(summary = "List ML Lab runs")
    public List<Map<String, Object>> listRuns() {
        return mlLabService.listRuns().stream().map(MlLabController::statusBody).toList();
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "ML Lab run status")
    public Map<String, Object> getRun(@PathVariable String id) {
        return statusBody(mlLabService.getRun(id));
    }

    @GetMapping(value = "/runs/{id}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "ML Lab run result", description = "Result JSON once DONE; 409 while still running")
    public ResponseEntity<String> getResult(@PathVariable String id) {
        MlLabRun run = mlLabService.getRun(id);
        if (run.getStatus() != MlLabRun.Status.DONE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("{\"status\":\"" + run.getStatus() + "\",\"message\":\"Result not ready\"}");
        }
        return ResponseEntity.ok(run.getResultJson());
    }

    private static Map<String, Object> statusBody(MlLabRun run) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", run.getId());
        body.put("status", run.getStatus().name());
        body.put("params", run.getParamsJson());
        body.put("createdAt", String.valueOf(run.getCreatedAt()));
        body.put("startedAt", String.valueOf(run.getStartedAt()));
        body.put("finishedAt", String.valueOf(run.getFinishedAt()));
        if (run.getErrorMessage() != null) {
            body.put("errorMessage", run.getErrorMessage());
        }
        return body;
    }
}
