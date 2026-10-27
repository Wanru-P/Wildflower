package com.floralens.di

import com.floralens.ml.FakeIdentifier
import com.floralens.ml.HybridIdentifier
import com.floralens.ml.PlantIdentifier
import com.floralens.ml.TfLiteIdentifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class RealIdentifier
@Qualifier annotation class DemoIdentifier

/**
 * Binds TfLite vs Fake vs the app-wide default. The unqualified binding now
 * points at HybridIdentifier, which routes to on-device or Pl@ntNet based on
 * the user's stored preference (see ml/HybridIdentifier.kt) — IdentifyPlant
 * and everything else that injects the unqualified PlantIdentifier keeps
 * working with zero changes.
 *
 * @RealIdentifier stays pinned to TfLiteIdentifier specifically — it's how
 * HybridIdentifier itself reaches the on-device model for its fallback path,
 * and how tests request the real classifier without the routing layer.
 * Tests and previews still request @DemoIdentifier for the deterministic Fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds @Singleton
    abstract fun bindDefaultIdentifier(impl: HybridIdentifier): PlantIdentifier

    @Binds @Singleton @RealIdentifier
    abstract fun bindRealIdentifier(impl: TfLiteIdentifier): PlantIdentifier

    @Binds @Singleton @DemoIdentifier
    abstract fun bindDemoIdentifier(impl: FakeIdentifier): PlantIdentifier
}