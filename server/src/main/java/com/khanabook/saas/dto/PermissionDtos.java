package com.khanabook.saas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class PermissionDtos {

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record GrantPermissionRequest(
        @NotNull Long userId,
        @NotBlank String permissionKey
    ) {}

    public record RevokePermissionRequest(
        @NotNull Long userId,
        @NotBlank String permissionKey
    ) {}

    public record BulkGrantRequest(
        @NotNull Long userId,
        @NotNull List<String> permissionKeys
    ) {}

    public record ApplyTemplateRequest(
        @NotNull Long userId,
        @NotNull Long templateId
    ) {}

    public record RequestPermissionRequest(
        @NotBlank String permissionKey,
        String reason
    ) {}

    public record ResolveRequestRequest(
        @NotBlank String action,  // "APPROVE" or "REJECT"
        String rejectionReason
    ) {}

    public record CreateTemplateRequest(
        @NotBlank String name,
        String description,
        @NotNull List<String> permissions
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    public record PermissionResponse(
        String permissionKey,
        String displayName,
        String category,
        boolean granted,
        Long grantedAt
    ) {}

    public record UserPermissionsResponse(
        Long userId,
        String userName,
        String role,
        List<PermissionResponse> permissions
    ) {}

    public record PermissionRequestResponse(
        Long id,
        Long userId,
        String userName,
        String permissionKey,
        String permissionDisplayName,
        String status,
        String reason,
        Long requestedAt,
        Long resolvedAt,
        String rejectionReason
    ) {}

    public record RoleTemplateResponse(
        Long id,
        String name,
        String description,
        List<String> permissions,
        boolean isDefault,
        Long createdAt
    ) {}

    /** Lightweight permission list for sync pull — just the keys the user has */
    public record SyncPermissionsResponse(
        Long userId,
        List<String> grantedPermissions,
        Long permissionsUpdatedAt
    ) {}
}
