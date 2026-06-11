package com.quantbackengine.backend.exception;

/**
 * Thrown when a new ML Lab run is submitted while another is QUEUED or RUNNING.
 * Maps to HTTP 409 — the system intentionally runs one job at a time.
 */
public class MlLabConflictException extends RuntimeException {

    public MlLabConflictException() {
        super("An ML Lab run is already in progress — one job at a time");
    }
}
