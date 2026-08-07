package com.khanabook.lite.pos.data.remote.api

import com.google.gson.annotations.SerializedName

data class PermissionSyncResponse(
    @SerializedName("userId") val userId: Long,
    @SerializedName("grantedPermissions") val grantedPermissions: List<String>,
    @SerializedName("permissionsUpdatedAt") val permissionsUpdatedAt: Long
)

data class PermissionRequestBody(
    @SerializedName("permissionKey") val permissionKey: String,
    @SerializedName("reason") val reason: String? = null
)

data class PermissionResolveBody(
    @SerializedName("action") val action: String,
    @SerializedName("rejectionReason") val rejectionReason: String? = null
)

data class PermissionGrantBody(
    @SerializedName("userId") val userId: Long,
    @SerializedName("permissionKey") val permissionKey: String
)

data class PermissionRevokeBody(
    @SerializedName("userId") val userId: Long,
    @SerializedName("permissionKey") val permissionKey: String
)

data class PermissionRequestDto(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("userName") val userName: String?,
    @SerializedName("permissionKey") val permissionKey: String,
    @SerializedName("permissionDisplayName") val permissionDisplayName: String?,
    @SerializedName("status") val status: String,
    @SerializedName("reason") val reason: String?,
    @SerializedName("requestedAt") val requestedAt: Long,
    @SerializedName("resolvedAt") val resolvedAt: Long?,
    @SerializedName("rejectionReason") val rejectionReason: String?
)


data class StaffListItem(
    @SerializedName("userId") val userId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("loginId") val loginId: String?,
    @SerializedName("role") val role: String,
    @SerializedName("active") val active: Boolean
)

data class UserPermissionsResponse(
    @SerializedName("userId") val userId: Long,
    @SerializedName("userName") val userName: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("permissions") val permissions: List<PermissionItemDto>
)

data class PermissionItemDto(
    @SerializedName("permissionKey") val permissionKey: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("category") val category: String,
    @SerializedName("granted") val granted: Boolean,
    @SerializedName("grantedAt") val grantedAt: Long?
)
