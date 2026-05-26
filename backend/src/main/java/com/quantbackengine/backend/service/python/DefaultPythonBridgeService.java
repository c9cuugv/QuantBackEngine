package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.exception.PythonBridgeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of PythonBridgeService.
 * 
 * Spawns Python subprocesses via ProcessBuilder, handles timeouts,
 * drains stderr asynchronously, and parses JSON output.
 */
@Service
@Slf4j
public class DefaultPythonBridgeService implements PythonBridgeService {

    private static final long AVAILABILITY_CACHE_DURATION_MS = 30_000; // 30 seconds

    private final PythonBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final Path basePath;

    // Cached availability result
    private final AtomicReference<CachedAvailability> cachedAvailability = new AtomicReference<>();

    private record CachedAvailability(boolean available, long timestamp) {}

    public DefaultPythonBridgeService(PythonBridgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.basePath = Paths.get(properties.scripts().basePath()).toAbsolutePath().normalize();
    }

    @Override
    public JsonNode invoke(String scriptRelativePath, List<String> args) throws PythonBridgeException {
        return executeScript(scriptRelativePath, args, null);
    }

    @Override
    public JsonNode invokeWithStdin(String scriptRelativePath, List<String> args, String stdinJson) throws PythonBridgeException {
        return executeScript(scriptRelativePath, args, stdinJson);
    }


    @Override
    public boolean isAvailable() {
        try {
            CachedAvailability cached = cachedAvailability.get();
            long now = System.currentTimeMillis();

            if (cached != null && (now - cached.timestamp()) < AVAILABILITY_CACHE_DURATION_MS) {
                log.debug("Using cached Python availability: {}", cached.available());
                return cached.available();
            }

            boolean available = checkPythonAvailable();
            cachedAvailability.set(new CachedAvailability(available, now));
            log.info("Python availability check: {}", available);
            return available;
        } catch (Exception e) {
            // Never throw from isAvailable - treat any error as unavailable
            log.debug("Python availability check failed: {}", e.getMessage());
            cachedAvailability.set(new CachedAvailability(false, System.currentTimeMillis()));
            return false;
        }
    }

    /**
     * Execute a Python script with optional stdin input.
     */
    private JsonNode executeScript(String scriptRelativePath, List<String> args, String stdinJson) throws PythonBridgeException {
        // Validate path - reject ".." segments before any processing
        validatePath(scriptRelativePath);

        // Resolve the full script path
        Path scriptPath = resolveScriptPath(scriptRelativePath);

        // Build the command
        List<String> command = buildCommand(scriptPath, args);

        log.debug("Executing Python script: {} with args: {}", scriptRelativePath, args);

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(false); // Keep stderr separate

            process = processBuilder.start();
            final Process runningProcess = process;

            // Drain stderr asynchronously on a virtual thread
            StringBuilder stderrBuilder = new StringBuilder();
            Thread stderrDrainer = Thread.ofVirtual().start(() -> drainStream(runningProcess.getErrorStream(), stderrBuilder));

            // Write stdin if provided
            if (stdinJson != null) {
                writeStdin(process, stdinJson);
            }

            // Read stdout with timeout
            String stdout = readStdoutWithTimeout(runningProcess, stderrDrainer, stderrBuilder);

            // Check exit code
            int exitCode = runningProcess.exitValue();
            if (exitCode != 0) {
                String stderr = stderrBuilder.toString().trim();
                log.warn("Python script {} exited with code {}, stderr: {}", scriptRelativePath, exitCode, stderr);
                throw new PythonBridgeException(
                        "Python script exited with code " + exitCode + ": " + (stderr.isEmpty() ? "no error output" : stderr));
            }

            // Check for empty stdout
            if (stdout == null || stdout.trim().isEmpty()) {
                String stderr = stderrBuilder.toString().trim();
                log.warn("Python script {} produced empty stdout, stderr: {}", scriptRelativePath, stderr);
                throw new PythonBridgeException("Python script produced empty output");
            }

            // Parse JSON
            return parseJson(stdout, scriptRelativePath);

        } catch (PythonBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing Python script {}: {}", scriptRelativePath, e.getMessage());
            throw new PythonBridgeException("Failed to execute Python script: " + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }


    /**
     * Validate that the script path does not contain path traversal segments.
     */
    private void validatePath(String scriptRelativePath) throws PythonBridgeException {
        if (scriptRelativePath == null || scriptRelativePath.isEmpty()) {
            throw new PythonBridgeException("Script path cannot be null or empty");
        }

        // Check for ".." segments in the path
        String normalized = scriptRelativePath.replace('\\', '/');
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new PythonBridgeException("Path traversal not allowed: " + scriptRelativePath);
            }
        }

        // Also check the normalized path doesn't escape base path
        Path resolved = basePath.resolve(scriptRelativePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new PythonBridgeException("Path traversal not allowed: " + scriptRelativePath);
        }
    }

    /**
     * Resolve the script path relative to the base path.
     */
    private Path resolveScriptPath(String scriptRelativePath) {
        return basePath.resolve(scriptRelativePath).normalize();
    }

    /**
     * Build the command list for ProcessBuilder.
     */
    private List<String> buildCommand(Path scriptPath, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(properties.python().executable());
        command.add(scriptPath.toString());
        if (args != null) {
            command.addAll(args);
        }
        return command;
    }

    /**
     * Write JSON to the process stdin and close the stream.
     */
    private void writeStdin(Process process, String stdinJson) throws IOException {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(stdinJson.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
    }

    /**
     * Read stdout with timeout, killing the process if it exceeds the limit.
     */
    private String readStdoutWithTimeout(Process process, Thread stderrDrainer, StringBuilder stderrBuilder) 
            throws PythonBridgeException {
        int timeoutSeconds = properties.python().timeoutSeconds();

        try {
            // Read stdout in a separate thread to allow timeout
            StringBuilder stdoutBuilder = new StringBuilder();
            Thread stdoutReader = Thread.ofVirtual().start(() -> drainStream(process.getInputStream(), stdoutBuilder));

            // Wait for process with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                // Timeout - kill the process
                log.warn("Python process timed out after {} seconds, killing forcibly", timeoutSeconds);
                process.destroyForcibly();
                
                // Wait a bit for stderr drainer to capture any final output
                stderrDrainer.join(1000);
                String stderr = stderrBuilder.toString().trim();
                
                throw new PythonBridgeException(
                        "Python script timed out after " + timeoutSeconds + " seconds" + 
                        (stderr.isEmpty() ? "" : ": " + stderr));
            }

            // Wait for stdout reader to complete
            stdoutReader.join(5000);
            
            // Wait for stderr drainer to complete
            stderrDrainer.join(1000);

            return stdoutBuilder.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PythonBridgeException("Python script execution interrupted", e);
        }
    }


    /**
     * Drain an input stream into a StringBuilder.
     * Used for reading stdout and stderr asynchronously.
     */
    private void drainStream(java.io.InputStream inputStream, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!builder.isEmpty()) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
            }
        } catch (IOException e) {
            log.debug("Error draining stream: {}", e.getMessage());
        }
    }

    /**
     * Parse JSON from stdout.
     */
    private JsonNode parseJson(String stdout, String scriptRelativePath) throws PythonBridgeException {
        try {
            return objectMapper.readTree(stdout);
        } catch (Exception e) {
            log.warn("Failed to parse JSON from script {}: {}", scriptRelativePath, e.getMessage());
            throw new PythonBridgeException("Failed to parse JSON output: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Python is available by running "python --version".
     */
    private boolean checkPythonAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.python().executable(), "--version");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read output (we don't really need it, just drain it)
            StringBuilder output = new StringBuilder();
            Thread drainer = Thread.ofVirtual().start(() -> drainStream(process.getInputStream(), output));

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            drainer.join(1000);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("Python version check output: {}", output.toString().trim());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.debug("Python availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
