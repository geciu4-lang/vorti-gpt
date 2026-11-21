package com.vortix.app.di

import com.vortix.app.data.battery.AndroidWakelockDiagnosticsRepository
import com.vortix.app.domain.repository.WakelockDiagnosticsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WakelockDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindWakelockDiagnosticsRepository(
        implementation: AndroidWakelockDiagnosticsRepository
    ): WakelockDiagnosticsRepository
}
