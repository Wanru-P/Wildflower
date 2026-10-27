package com.floralens.di

import android.content.Context
import androidx.room.Room
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.ObservationDao
import com.floralens.data.db.PlantDao
import com.floralens.data.db.PlantInfoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FloraDatabase =
        Room.databaseBuilder(context, FloraDatabase::class.java, "floralens.db")
            .addMigrations(FloraDatabase.MIGRATION_1_2, FloraDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun providePlantDao(db: FloraDatabase): PlantDao = db.plantDao()

    @Provides
    fun provideObservationDao(db: FloraDatabase): ObservationDao = db.observationDao()

    @Provides
    fun providePlantInfoDao(db: FloraDatabase): PlantInfoDao = db.plantInfoDao()
}
