package com.vortix.app.di

import com.vortix.app.data.repository.ChargingProfileRepositoryImpl
import com.vortix.app.domain.repository.ChargingProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChargingProfileModule {
    @Binds
    @Singleton
    abstract fun bindChargingProfileRepository(
        implementation: ChargingProfileRepositoryImpl
    ): ChargingProfileRepository
}
