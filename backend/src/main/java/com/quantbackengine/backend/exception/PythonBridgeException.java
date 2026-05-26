package com.quantbackengine.backend.exception;

/**
 * Unchecked exception thrown when Python bridge operations fail.
 * 
 * This includes:
 * - Path traversal attempts (.. segments in script path)
 * - Process timeout
 * - Non-zero exit code
 * - Empty stdout
 * - JSON parsing failures
 */
public class PythonBridgeException extends RuntimeException {

    public PythonBridgeException(String message) {
        super(message);
    }

    public PythonBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
