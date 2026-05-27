package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.config.PythonBridgeProperties;
import com.quantbackengine.backend.exception.PythonBridgeException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for path traversal rejection in PythonBridgeService.
 * 
 * **Validates: Requirement 1.2**
 * 
 * Property 9: Path traversal is always rejected
 * For any script path argument containing ".." segments, PythonBridgeService.invoke()
 * SHALL throw PythonBridgeException without spawning a process.
 */
class PythonBridgeServicePathTraversalPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a test instance of DefaultPythonBridgeService with mock properties.
     * The service is configured to use a non-existent Python executable to ensure
     * no actual processes are spawned during path validation tests.
     */
    private DefaultPythonBridgeService createService() {
        PythonBridgeProperties properties = new PythonBridgeProperties(
                new PythonBridgeProperties.Scripts("FinceptTerminal/fincept-qt/scripts"),
                new PythonBridgeProperties.Python(
                        "nonexistent-python-for-test",
                        60,
                        new PythonBridgeProperties.Python.MarketData(false)
                )
        );
        return new DefaultPythonBridgeService(properties, objectMapper);
    }

    /**
     * **Property 9: Path traversal is always rejected**
     * **Validates: Requirement 1.2**
     * 
     * For any script path containing ".." segments, invoke() SHALL throw
     * PythonBridgeException without spawning a process.
     */
    @Property(tries = 100)
    void pathTraversalAlwaysRejected(@ForAll("pathsWithTraversal") String pathWithTraversal) {
        DefaultPythonBridgeService service = createService();

        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invoke(pathWithTraversal, Collections.emptyList()),
                "Path traversal should be rejected for path: " + pathWithTraversal
        );

        assertTrue(
                exception.getMessage().contains("Path traversal not allowed"),
                "Exception message should indicate path traversal rejection"
        );
    }

    /**
     * **Property 9: Path traversal is always rejected (with stdin variant)**
     * **Validates: Requirement 1.2**
     * 
     * For any script path containing ".." segments, invokeWithStdin() SHALL throw
     * PythonBridgeException without spawning a process.
     */
    @Property(tries = 100)
    void pathTraversalAlwaysRejectedWithStdin(@ForAll("pathsWithTraversal") String pathWithTraversal) {
        DefaultPythonBridgeService service = createService();

        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invokeWithStdin(pathWithTraversal, Collections.emptyList(), "{}"),
                "Path traversal should be rejected for path: " + pathWithTraversal
        );

        assertTrue(
                exception.getMessage().contains("Path traversal not allowed"),
                "Exception message should indicate path traversal rejection"
        );
    }

    /**
     * Provides arbitrary paths that contain ".." segments in various positions.
     * Generates paths like:
     * - "../script.py"
     * - "dir/../script.py"
     * - "dir/subdir/../../script.py"
     * - "..\\script.py" (Windows-style)
     * - Mixed patterns
     */
    @Provide
    Arbitrary<String> pathsWithTraversal() {
        // Generate various path patterns containing ".."
        return Arbitraries.oneOf(
                // Simple traversal at start
                simpleTraversalAtStart(),
                // Traversal in middle of path
                traversalInMiddle(),
                // Multiple traversals
                multipleTraversals(),
                // Windows-style backslash traversal
                windowsStyleTraversal(),
                // Mixed forward and backslash
                mixedSlashTraversal()
        );
    }

    private Arbitrary<String> simpleTraversalAtStart() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "../" + name + ".py");
    }

    private Arbitrary<String> traversalInMiddle() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
        ).as((dir, file) -> dir + "/../" + file + ".py");
    }

    private Arbitrary<String> multipleTraversals() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
        ).as((dir1, dir2, file) -> dir1 + "/" + dir2 + "/../../" + file + ".py");
    }

    private Arbitrary<String> windowsStyleTraversal() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "..\\" + name + ".py");
    }

    private Arbitrary<String> mixedSlashTraversal() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
        ).as((dir, file) -> dir + "\\..\\" + file + ".py");
    }

    /**
     * Additional property: Paths with ".." anywhere in segments should be rejected.
     * This tests edge cases like "foo..bar" which should NOT be rejected (no segment equals ".."),
     * vs "foo/../bar" which SHOULD be rejected.
     */
    @Property(tries = 50)
    void onlyActualTraversalSegmentsAreRejected(
            @ForAll("pathsWithActualTraversalSegment") String pathWithActualSegment) {
        DefaultPythonBridgeService service = createService();

        PythonBridgeException exception = assertThrows(
                PythonBridgeException.class,
                () -> service.invoke(pathWithActualSegment, Collections.emptyList()),
                "Actual '..' segment should be rejected for path: " + pathWithActualSegment
        );

        assertTrue(
                exception.getMessage().contains("Path traversal not allowed"),
                "Exception message should indicate path traversal rejection"
        );
    }

    @Provide
    Arbitrary<String> pathsWithActualTraversalSegment() {
        // Generate paths where ".." is an actual path segment (between slashes or at boundaries)
        return Arbitraries.oneOf(
                // ".." at start
                Arbitraries.just(".."),
                Arbitraries.just("../"),
                Arbitraries.just("../script.py"),
                // ".." in middle
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .map(dir -> dir + "/../script.py"),
                // ".." at end (unusual but should still be rejected)
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .map(dir -> dir + "/.."),
                // Multiple ".." segments
                Arbitraries.just("../../script.py"),
                Arbitraries.just("dir/../../script.py")
        );
    }
}
