package com.floralens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides the real system Clock for production use. ComputeCareStatus takes
 * Clock as a plain constructor param (no default — Dagger doesn't reliably
 * support default values on @Inject constructors), so production code needs
 * this binding. Tests construct ComputeCareStatus directly with a fixed
 * Clock (e.g. Clock.fixed(...)) and never go through Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}