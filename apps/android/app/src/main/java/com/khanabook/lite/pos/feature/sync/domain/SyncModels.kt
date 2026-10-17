package com.khanabook.lite.pos.feature.sync.domain

data class ServerIdMapping(
    val id: Long,
    val serverId: Long
)

/**
 * Per-item sync state we must read *before* overwriting, so that a pull from an older
 * server cannot silently downgrade a field the server does not know about yet.
 *
 * Named for its original purpose; it now also carries [hasVariants].
 */
data class MenuItemImageInfo(
    val id: Long,
    val imageUrl: String?,
    val imageVersion: Int,
    val hasVariants: Boolean = false
)
