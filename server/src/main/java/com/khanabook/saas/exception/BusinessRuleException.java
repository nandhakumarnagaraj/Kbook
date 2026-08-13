package com.khanabook.saas.exception;

/**
 * Thrown when a business rule is violated (e.g., duplicate sub-merchant,
 * split label already exists, sub-merchant not in required state).
 * Maps to HTTP 422 (Unprocessable Entity) via {@link GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {

    private final String rule;

    public BusinessRuleException(String message) {
        super(message);
        this.rule = null;
    }

    public BusinessRuleException(String message, String rule) {
        super(message);
        this.rule = rule;
    }

    public String getRule() { return rule; }
}
