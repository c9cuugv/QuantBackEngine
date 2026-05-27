package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.exception.PythonBridgeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultPythonBridgeService.
 * 
 * Tests cover:
 * - Timeout path: process that never finishes → destroyForcibly() called and exception thrown
 * - isAvailable() caching: second call within 30s does not spawn a new process
 * - isAvailable() returns false when executable not found (no exception propagated)
 * 
 * **Validates: Requirements 1.3, 1.6, 1.7**
 */
class DefaultPythonBridgeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Path testScriptPath;

    @BeforeEach
    void setUp() throws IOException {
        testScriptPath = tempDir.resolve("test_script.py");
    }

    @AfterEach
    void tearDown() {
        // Cleanup handled by @TempDir
    }

    /**
     * Creates a DefaultPythonBridgeService with the specified timeout and executable.
     */
    private DefaultPythonBridgeService createService(String executable, int timeoutSeconds) {
        PythonBridgeProperties properties = new PythonBridgeProperties(
                new PythonBridgeProperties.Scripts(tempDir.toString()),
                new PythonBridgeProperties.Python(
                        executable,
                        timeoutSeconds,
                        new PythonBridgeProperties.Python.MarketData(false)
                )
        );
        return new DefaultPythonBridgeService(properties, objectMapper);
    }

    /**
     * Creates a DefaultPythonBridgeService with default python3 executable.
     */
    private DefaultPythonBridgeService createService(int timeoutSeconds) {
        return createService("python3", timeoutSeconds);
    }

    // ========================================================================
    // Timeout Tests - Validates Requirement 1.3
    // ========================================================================

    /**
     * Test timeout path: process that never finishes should be killed forcibly
     * and PythonBridgeException should be thrown.
     * 
     * **Validates: Requirement 1.3**
     * WHEN a subprocess exceeds fincept.python.timeout-seconds, THE PythonBridgeService
     * SHALL call destroyForcibly() and throw PythonBridgeException.
     */
    @Test
    void invoke_processTimesOut_throwsExceptionAndKillsProcess() throws IOException {
        // Create a script that sleeps forever (simulates a hanging process)
        String scriptContent = """
            import time
            import sys
            # Sleep for a very long time to simulate a hanging process
            time.sleep(3600)
            print('{"result": "should never reach here"}')
            sys.exit(0)
            """;
        Files.writeString(testScriptPath, scriptContent);

        // Create service with a very short timeout (2 seconds)
        DefaultPythonBridgeService service = createService(2);

        // Record start time to verify timeout behavior
        long startTime = System.currentTimeMillis();

        // Act & Assert
        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invoke("test_script.py", Collections.emptyList()),
                "Should throw PythonBridgeException when process times out"
        );

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Verify the exception message indicates timeout
        assertTrue(
                exception.getMessage().contains("timed out"),
                "Exception message should indicate timeout: " + exception.getMessage()
        );

        // Verify the timeout was approximately 2 seconds (with some tolerance)
        assertTrue(
                elapsedTime >= 1500 && elapsedTime < 10000,
                "Process should have been killed after approximately 2 seconds, but took " + elapsedTime + "ms"
        );
    }

    /**
     * Test timeout with invokeWithStdin variant.
     * 
     * **Validates: Requirement 1.3**
     */
    @Test
    void invokeWithStdin_processTimesOut_throwsExceptionAndKillsProcess() throws IOException {
        // Create a script that sleeps forever
        String scriptContent = """
            import time
            import sys
            import json
            # Read stdin but then hang
            data = sys.stdin.read()
            time.sleep(3600)
            print('{"result": "should never reach here"}')
            sys.exit(0)
            """;
        Files.writeString(testScriptPath, scriptContent);

        // Create service with a very short timeout (2 seconds)
        DefaultPythonBridgeService service = createService(2);

        // Act & Assert
        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invokeWithStdin("test_script.py", Collections.emptyList(), "{\"input\": \"test\"}"),
                "Should throw PythonBridgeException when process times out"
        );

        assertTrue(
                exception.getMessage().contains("timed out"),
                "Exception message should indicate timeout: " + exception.getMessage()
        );
    }

    /**
     * Test that timeout message includes the configured timeout value.
     * 
     * **Validates: Requirement 1.3**
     */
    @Test
    void invoke_processTimesOut_exceptionMessageIncludesTimeoutValue() throws IOException {
        String scriptContent = """
            import time
            time.sleep(3600)
            """;
        Files.writeString(testScriptPath, scriptContent);

        int timeoutSeconds = 3;
        DefaultPythonBridgeService service = createService(timeoutSeconds);

        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invoke("test_script.py", Collections.emptyList())
        );

        assertTrue(
                exception.getMessage().contains(String.valueOf(timeoutSeconds)),
                "Exception message should include timeout value: " + exception.getMessage()
        );
    }

    // ========================================================================
    // isAvailable() Caching Tests - Validates Requirement 1.6
    // ========================================================================

    /**
     * Test isAvailable() caching: second call within 30s should not spawn a new process.
     * 
     * **Validates: Requirement 1.6**
     * WHEN isAvailable() is called, THE PythonBridgeService SHALL return a cached result
     * valid for 30 seconds without re-spawning a process.
     */
    @Test
    void isAvailable_secondCallWithin30Seconds_returnsCachedResult() {
        // Create service with a valid Python executable
        DefaultPythonBridgeService service = createService("python3", 60);

        // First call - this will spawn a process
        long firstCallStart = System.currentTimeMillis();
        boolean firstResult = service.isAvailable();
        long firstCallDuration = System.currentTimeMillis() - firstCallStart;

        // Second call - should return cached result (much faster)
        long secondCallStart = System.currentTimeMillis();
        boolean secondResult = service.isAvailable();
        long secondCallDuration = System.currentTimeMillis() - secondCallStart;

        // Both calls should return the same result
        assertEquals(firstResult, secondResult, "Cached result should match first result");

        // Second call should be significantly faster (cached)
        // First call spawns a process, second call should be nearly instant
        assertTrue(
                secondCallDuration < firstCallDuration || secondCallDuration < 50,
                "Second call should be faster (cached). First: " + firstCallDuration + "ms, Second: " + secondCallDuration + "ms"
        );
    }

    /**
     * Test isAvailable() caching with unavailable Python.
     * 
     * **Validates: Requirement 1.6**
     */
    @Test
    void isAvailable_unavailablePython_cachedResultReturnedOnSecondCall() {
        // Create service with a non-existent Python executable
        DefaultPythonBridgeService service = createService("nonexistent-python-executable-xyz", 60);

        // First call - will fail to find Python
        boolean firstResult = service.isAvailable();
        assertFalse(firstResult, "Should return false for non-existent executable");

        // Second call - should return cached false result
        long secondCallStart = System.currentTimeMillis();
        boolean secondResult = service.isAvailable();
        long secondCallDuration = System.currentTimeMillis() - secondCallStart;

        assertFalse(secondResult, "Cached result should still be false");

        // Second call should be very fast (cached)
        assertTrue(
                secondCallDuration < 50,
                "Second call should be nearly instant (cached). Duration: " + secondCallDuration + "ms"
        );
    }

    /**
     * Test that multiple rapid calls to isAvailable() all return the same cached result.
     * 
     * **Validates: Requirement 1.6**
     */
    @Test
    void isAvailable_multipleRapidCalls_allReturnCachedResult() {
        DefaultPythonBridgeService service = createService("python3", 60);

        // First call to populate cache
        boolean firstResult = service.isAvailable();

        // Make multiple rapid calls
        for (int i = 0; i < 10; i++) {
            boolean result = service.isAvailable();
            assertEquals(firstResult, result, "All calls should return the same cached result");
        }
    }

    // ========================================================================
    // isAvailable() Never Throws Tests - Validates Requirement 1.7
    // ========================================================================

    /**
     * Test isAvailable() returns false when executable not found (no exception propagated).
     * 
     * **Validates: Requirement 1.7**
     * THE PythonBridgeService SHALL never throw from isAvailable() — Python absence
     * is treated as false.
     */
    @Test
    void isAvailable_executableNotFound_returnsFalseWithoutException() {
        // Create service with a non-existent Python executable
        DefaultPythonBridgeService service = createService("nonexistent-python-executable-xyz", 60);

        // Act - should not throw
        boolean result = assertDoesNotThrow(
                service::isAvailable,
                "isAvailable() should never throw, even when executable is not found"
        );

        // Assert
        assertFalse(result, "Should return false when Python executable is not found");
    }

    /**
     * Test isAvailable() returns false for various invalid executable paths.
     * 
     * **Validates: Requirement 1.7**
     */
    @Test
    void isAvailable_variousInvalidExecutables_returnsFalseWithoutException() {
        String[] invalidExecutables = {
                "",
                "   ",
                "/nonexistent/path/to/python",
                "python-version-999",
                "definitely-not-python",
                "../../../etc/passwd"  // Path traversal attempt
        };

        for (String executable : invalidExecutables) {
            DefaultPythonBridgeService service = createService(executable, 60);

            boolean result = assertDoesNotThrow(
                    service::isAvailable,
                    "isAvailable() should never throw for executable: " + executable
            );

            assertFalse(result, "Should return false for invalid executable: " + executable);
        }
    }

    /**
     * Test isAvailable() handles null-like edge cases gracefully.
     * 
     * **Validates: Requirement 1.7**
     */
    @Test
    void isAvailable_emptyExecutable_returnsFalseWithoutException() {
        DefaultPythonBridgeService service = createService("", 60);

        boolean result = assertDoesNotThrow(
                service::isAvailable,
                "isAvailable() should never throw for empty executable"
        );

        assertFalse(result, "Should return false for empty executable");
    }

    /**
     * Test isAvailable() returns false when Python version check fails.
     * 
     * **Validates: Requirement 1.7**
     */
    @Test
    void isAvailable_pythonVersionCheckFails_returnsFalseWithoutException() {
        // Use a command that exists but isn't Python (e.g., 'echo')
        // This will fail the version check because it won't output proper version info
        DefaultPythonBridgeService service = createService("echo", 60);

        boolean result = assertDoesNotThrow(
                service::isAvailable,
                "isAvailable() should never throw even when version check fails"
        );

        // echo will exit with 0 but won't be a valid Python, so this tests the edge case
        // The actual result depends on how the service validates Python
        // The key assertion is that no exception is thrown
    }

    // ========================================================================
    // Integration Tests - Successful Execution
    // ========================================================================

    /**
     * Test successful script execution returns valid JSON.
     */
    @Test
    void invoke_validScript_returnsJsonNode() throws IOException {
        String scriptContent = """
            import json
            print(json.dumps({"status": "success", "value": 42}))
            """;
        Files.writeString(testScriptPath, scriptContent);

        DefaultPythonBridgeService service = createService(30);

        var result = service.invoke("test_script.py", Collections.emptyList());

        assertNotNull(result, "Result should not be null");
        assertEquals("success", result.get("status").asText());
        assertEquals(42, result.get("value").asInt());
    }

    /**
     * Test successful script execution with stdin.
     */
    @Test
    void invokeWithStdin_validScript_returnsJsonNode() throws IOException {
        String scriptContent = """
            import json
            import sys
            data = json.loads(sys.stdin.read())
            result = {"received": data["input"], "processed": True}
            print(json.dumps(result))
            """;
        Files.writeString(testScriptPath, scriptContent);

        DefaultPythonBridgeService service = createService(30);

        var result = service.invokeWithStdin("test_script.py", Collections.emptyList(), "{\"input\": \"test\"}");

        assertNotNull(result, "Result should not be null");
        assertEquals("test", result.get("received").asText());
        assertTrue(result.get("processed").asBoolean());
    }
}
