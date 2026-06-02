package com.cengyi.wifitimer.widget

import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.TargetConfigRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRepositoryEntryPoint {
    fun sessionRepository(): SessionRepository
    fun targetConfigRepository(): TargetConfigRepository
    fun ignoreWindowRepository(): IgnoreWindowRepository
}