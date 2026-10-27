package com.floralens.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.floralens.data.db.entity.ObservationEntity
import com.floralens.data.db.entity.PlantEntity
import com.floralens.data.db.entity.PlantInfoEntity
import com.floralens.domain.model.SpeciesKey

@Database(
    entities = [PlantEntity::class, ObservationEntity::class, PlantInfoEntity::class, PhotoOperation::class],
    version = 3,
    exportSchema = true,
)
abstract class FloraDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun observationDao(): ObservationDao
    abstract fun plantInfoDao(): PlantInfoDao
    abstract fun archiveDao(): ArchiveDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN speciesKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plants ADD COLUMN coverObservationId INTEGER")
                db.execSQL("ALTER TABLE observations ADD COLUMN identificationJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS photo_operations (operationId TEXT NOT NULL PRIMARY KEY, plantId INTEGER NOT NULL, observationId INTEGER, photoUri TEXT)")
                db.query("SELECT id, scientificName, commonName, photoUri, createdAtEpochMs FROM plants").use { rows ->
                    while (rows.moveToNext()) {
                        val id = rows.getLong(0)
                        db.execSQL("UPDATE plants SET speciesKey = ? WHERE id = ?", arrayOf(SpeciesKey.fromLegacy(rows.getString(1), rows.getString(2)), id))
                        val uri = if (rows.isNull(3)) null else rows.getString(3).takeIf { it.isNotBlank() }
                        if (uri != null) {
                            var cover: Long? = null
                            db.query("SELECT id FROM observations WHERE plantId = ? AND photoUri = ? ORDER BY timestampEpochMs, id LIMIT 1", arrayOf(id, uri)).use { matches ->
                                if (matches.moveToFirst()) cover = matches.getLong(0)
                            }
                            if (cover == null) {
                                // Preserve an otherwise orphaned legacy cover, without claiming an identification.
                                db.execSQL("INSERT INTO observations (plantId,type,note,photoUri,latitude,longitude,timestampEpochMs,identificationJson) VALUES (?, 'PHOTO_OBSERVATION', '从旧版封面迁移（原观察时间未知，沿用收藏时间）', ?, NULL, NULL, ?, NULL)", arrayOf(id, uri, rows.getLong(4)))
                                db.query("SELECT last_insert_rowid()").use { inserted -> inserted.moveToFirst(); cover = inserted.getLong(0) }
                            }
                            db.execSQL("UPDATE plants SET coverObservationId = ? WHERE id = ?", arrayOf(cover, id))
                        }
                    }
                }
            }
        }
        /**
         * v1 → v2: adds the plant_info table (offline-saved "Learn more"
         * details). Additive only — existing plants/observations untouched.
         * Column types/nullability must match PlantInfoEntity exactly or
         * Room's schema validation throws at open.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `plant_info` (
                        `scientificName` TEXT NOT NULL,
                        `commonName` TEXT,
                        `insightsJson` TEXT NOT NULL,
                        `geoPointsJson` TEXT NOT NULL,
                        `occurrenceJson` TEXT,
                        `savedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`scientificName`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
