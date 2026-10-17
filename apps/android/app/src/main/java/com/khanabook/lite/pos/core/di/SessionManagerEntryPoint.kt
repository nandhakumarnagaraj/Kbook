package com.khanabook.lite.pos.core.di

import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionManagerEntryPoint {
    fun sessionManager(): SessionManager
}
