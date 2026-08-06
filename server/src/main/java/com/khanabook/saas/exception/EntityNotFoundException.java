package com.khanabook.saas.exception;

/**
 * Thrown when a requested entity does not exist in the database.
 * Maps to HTTP 404 via {@link GlobalExceptionHandler}.
 */
public class EntityNotFoundException extends RuntimeException {

    private final String entityType;
    private final Object entityId;

    public EntityNotFoundException(String entityType, Object entityId) {
        super(entityType + " not found: " + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public EntityNotFoundException(String message) {
        super(message);
        this.entityType = "Entity";
        this.entityId = null;
    }

    public String getEntityType() { return entityType; }
    public Object getEntityId()   { return entityId; }
}
