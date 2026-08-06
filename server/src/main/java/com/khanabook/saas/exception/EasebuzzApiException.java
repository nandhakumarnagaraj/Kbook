package com.khanabook.saas.exception;

/**
 * Thrown when the Easebuzz payment gateway returns an error response
 * or is unreachable. Maps to HTTP 502 via {@link GlobalExceptionHandler}.
 */
public class EasebuzzApiException extends RuntimeException {

    private final String apiEndpoint;

    public EasebuzzApiException(String message, String apiEndpoint) {
        super(message);
        this.apiEndpoint = apiEndpoint;
    }

    public EasebuzzApiException(String message) {
        super(message);
        this.apiEndpoint = null;
    }

    public EasebuzzApiException(String message, Throwable cause) {
        super(message, cause);
        this.apiEndpoint = null;
    }

    public String getApiEndpoint() { return apiEndpoint; }
}
