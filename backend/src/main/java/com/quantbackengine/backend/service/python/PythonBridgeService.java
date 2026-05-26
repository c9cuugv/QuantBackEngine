package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.quantbackengine.backend.exception.PythonBridgeException;

import java.util.List;

/**
 * Service interface for invoking Python scripts via subprocess.
 * 
 * Provides a managed subprocess layer for all Python script invocations,
 * ensuring consistent timeout handling, error management, and JSON parsing.
 */
public interface PythonBridgeService {

    /**
     * Invoke a Python script with command-line arguments.
     * 
     * @param scriptRelativePath path to the script relative to the configured base path
     * @param args command-line arguments to pass to the script
     * @return parsed JSON output from the script's stdout
     * @throws PythonBridgeException if path contains ".." segments, process times out,
     *         exits with non-zero code, produces empty stdout, or JSON parsing fails
     */
    JsonNode invoke(String scriptRelativePath, List<String> args) throws PythonBridgeException;

    /**
     * Invoke a Python script with command-line arguments and stdin input.
     * 
     * @param scriptRelativePath path to the script relative to the configured base path
     * @param args command-line arguments to pass to the script
     * @param stdinJson JSON string to write to the process stdin
     * @return parsed JSON output from the script's stdout
     * @throws PythonBridgeException if path contains ".." segments, process times out,
     *         exits with non-zero code, produces empty stdout, or JSON parsing fails
     */
    JsonNode invokeWithStdin(String scriptRelativePath, List<String> args, String stdinJson) throws PythonBridgeException;

    /**
     * Check if Python is available and executable.
     * 
     * Result is cached for 30 seconds to avoid repeated subprocess spawns.
     * This method never throws — Python absence is treated as false.
     * 
     * @return true if Python executable is available and working, false otherwise
     */
    boolean isAvailable();
}
