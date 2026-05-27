package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.exception.PythonBridgeException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for non-zero exit code and empty stdout handling in PythonBridgeService.
 * 
 * **Validates: Requirement 1.4**
 * 
 * Property 10: Non-zero exit or empty stdout always throws
 * For any subprocess invocation that exits with a non-zero code or produces empty stdout,
 * PythonBridgeService SHALL throw PythonBridgeException.
 */
class PythonBridgeServiceExitCodePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path tempDir;
    private Path testScriptPath;

    /**
     * Creates a temporary directory with a test Python script that can be configured
     * to exit with specific codes or produce specific output.
     */
    private void setupTestEnvironment() throws IOException {
        tempDir = Files.createTempDirectory("python-bridge-test");
        testScriptPath = tempDir.resolve("test_script.py");
    }

    /**
     * Cleans up the temporary test environment.
     */
    private void cleanupTestEnvironment() {
        try {
            if (testScriptPath != null && Files.exists(testScriptPath)) {
                Files.delete(testScriptPath);
            }
            if (tempDir != null && Files.exists(tempDir)) {
                Files.delete(tempDir);
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Creates a test Python script that exits with the specified exit code.
     */
    private void createExitCodeScript(int exitCode) throws IOException {
        String scriptContent = String.format("""
            import sys
            print('{"status": "error"}')
            sys.exit(%d)
            """, exitCode);
        Files.writeString(testScriptPath, scriptContent);
    }

    /**
     * Creates a test Python script that produces empty stdout.
     */
    private void createEmptyOutputScript() throws IOException {
        String scriptContent = """
            import sys
            # Produce no output
            sys.exit(0)
            """;
        Files.writeString(testScriptPath, scriptContent);
    }

    /**
     * Creates a test Python script that produces only whitespace.
     */
    private void createWhitespaceOnlyScript() throws IOException {
        String scriptContent = """
            import sys
            print('   ')
            print('\\t\\n')
            sys.exit(0)
            """;
        Files.writeString(testScriptPath, scriptContent);
    }

    /**
     * Creates a DefaultPythonBridgeService configured to use the temp directory.
     */
    private DefaultPythonBridgeService createService() {
        PythonBridgeProperties properties = new PythonBridgeProperties(
                new PythonBridgeProperties.Scripts(tempDir.toString()),
                new PythonBridgeProperties.Python(
                        "python3",
                        30,
                        new PythonBridgeProperties.Python.MarketData(false)
                )
        );
        return new DefaultPythonBridgeService(properties, objectMapper);
    }

    /**
     * **Property 10: Non-zero exit code always throws**
     * **Validates: Requirement 1.4**
     * 
     * For any subprocess invocation that exits with a non-zero code,
     * PythonBridgeService SHALL throw PythonBridgeException.
     */
    @Property(tries = 50)
    void nonZeroExitCodeAlwaysThrows(@ForAll @IntRange(min = 1, max = 255) int exitCode) {
        try {
            setupTestEnvironment();
            createExitCodeScript(exitCode);
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invoke("test_script.py", Collections.emptyList()),
                    "Non-zero exit code " + exitCode + " should throw PythonBridgeException"
            );

            assertTrue(
                    exception.getMessage().contains("exited with code " + exitCode),
                    "Exception message should contain exit code: " + exception.getMessage()
            );
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    /**
     * **Property 10: Non-zero exit code always throws (with stdin variant)**
     * **Validates: Requirement 1.4**
     * 
     * For any subprocess invocation with stdin that exits with a non-zero code,
     * PythonBridgeService SHALL throw PythonBridgeException.
     */
    @Property(tries = 50)
    void nonZeroExitCodeAlwaysThrowsWithStdin(@ForAll @IntRange(min = 1, max = 255) int exitCode) {
        try {
            setupTestEnvironment();
            createExitCodeScript(exitCode);
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invokeWithStdin("test_script.py", Collections.emptyList(), "{}"),
                    "Non-zero exit code " + exitCode + " should throw PythonBridgeException"
            );

            assertTrue(
                    exception.getMessage().contains("exited with code " + exitCode),
                    "Exception message should contain exit code: " + exception.getMessage()
            );
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    /**
     * **Property 10: Empty stdout always throws**
     * **Validates: Requirement 1.4**
     * 
     * For any subprocess invocation that produces empty stdout,
     * PythonBridgeService SHALL throw PythonBridgeException.
     */
    @Property(tries = 10)
    void emptyStdoutAlwaysThrows() {
        try {
            setupTestEnvironment();
            createEmptyOutputScript();
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invoke("test_script.py", Collections.emptyList()),
                    "Empty stdout should throw PythonBridgeException"
            );

            assertTrue(
                    exception.getMessage().contains("empty output"),
                    "Exception message should indicate empty output: " + exception.getMessage()
            );
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    /**
     * **Property 10: Whitespace-only stdout always throws**
     * **Validates: Requirement 1.4**
     * 
     * For any subprocess invocation that produces only whitespace stdout,
     * PythonBridgeService SHALL throw PythonBridgeException.
     */
    @Property(tries = 10)
    void whitespaceOnlyStdoutAlwaysThrows() {
        try {
            setupTestEnvironment();
            createWhitespaceOnlyScript();
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invoke("test_script.py", Collections.emptyList()),
                    "Whitespace-only stdout should throw PythonBridgeException"
            );

            assertTrue(
                    exception.getMessage().contains("empty output"),
                    "Exception message should indicate empty output: " + exception.getMessage()
            );
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    /**
     * **Property 10: Various non-zero exit codes with different output scenarios**
     * **Validates: Requirement 1.4**
     * 
     * Tests combinations of non-zero exit codes with various output scenarios.
     */
    @Property(tries = 30)
    void nonZeroExitWithVariousOutputAlwaysThrows(
            @ForAll @IntRange(min = 1, max = 255) int exitCode,
            @ForAll("outputScenarios") String outputScenario) {
        try {
            setupTestEnvironment();
            createScriptWithExitAndOutput(exitCode, outputScenario);
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invoke("test_script.py", Collections.emptyList()),
                    "Non-zero exit code " + exitCode + " with output scenario '" + outputScenario + "' should throw"
            );

            // Exception should be thrown regardless of output content when exit code is non-zero
            assertNotNull(exception.getMessage(), "Exception message should not be null");
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    /**
     * Creates a test Python script with specified exit code and output scenario.
     */
    private void createScriptWithExitAndOutput(int exitCode, String outputScenario) throws IOException {
        String printStatement = switch (outputScenario) {
            case "valid_json" -> "print('{\"data\": \"test\"}')";
            case "invalid_json" -> "print('not json')";
            case "empty" -> "# no output";
            case "whitespace" -> "print('   ')";
            case "stderr_only" -> "import sys; sys.stderr.write('error message')";
            default -> "print('default output')";
        };

        String scriptContent = String.format("""
            import sys
            %s
            sys.exit(%d)
            """, printStatement, exitCode);
        Files.writeString(testScriptPath, scriptContent);
    }

    @Provide
    Arbitrary<String> outputScenarios() {
        return Arbitraries.of("valid_json", "invalid_json", "empty", "whitespace", "stderr_only");
    }

    /**
     * **Property 10: Common error exit codes always throw**
     * **Validates: Requirement 1.4**
     * 
     * Tests common Unix/Python error exit codes to ensure they all throw.
     */
    @Property(tries = 20)
    void commonErrorExitCodesAlwaysThrow(@ForAll("commonExitCodes") int exitCode) {
        try {
            setupTestEnvironment();
            createExitCodeScript(exitCode);
            DefaultPythonBridgeService service = createService();

            PythonBridgeException exception = assertThrows(
                    PythonBridgeException.class,
                    () -> service.invoke("test_script.py", Collections.emptyList()),
                    "Common error exit code " + exitCode + " should throw PythonBridgeException"
            );

            assertTrue(
                    exception.getMessage().contains("exited with code"),
                    "Exception message should indicate exit code"
            );
        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
        } finally {
            cleanupTestEnvironment();
        }
    }

    @Provide
    Arbitrary<Integer> commonExitCodes() {
        // Common Unix/Python error exit codes
        return Arbitraries.of(
                1,   // General errors
                2,   // Misuse of shell command
                126, // Command invoked cannot execute
                127, // Command not found
                128, // Invalid argument to exit
                130, // Script terminated by Ctrl+C
                137, // Process killed (SIGKILL)
                139, // Segmentation fault
                143, // Process terminated (SIGTERM)
                255  // Exit status out of range
        );
    }
}
