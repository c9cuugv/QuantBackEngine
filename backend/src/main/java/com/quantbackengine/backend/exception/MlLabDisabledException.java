package com.quantbackengine.backend.exception;

/**
 * Thrown when ML Lab endpoints are hit on a deployment where the feature is
 * disabled ({@code fincept.python.mllab.enabled=false}). Maps to HTTP 404.
 */
public class MlLabDisabledException extends RuntimeException {

    public MlLabDisabledException() {
        super("ML Lab is not available on this deployment");
    }
}
