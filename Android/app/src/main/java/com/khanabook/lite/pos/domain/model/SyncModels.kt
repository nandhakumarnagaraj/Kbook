package com.khanabook.lite.pos.domain.model

data class ServerIdMapping(
    val id: Long,
    val serverId: Long
)

data class MenuItemImageInfo(
    val id: Long,
    val imageUrl: String?,
    val imageVersion: Int
)
