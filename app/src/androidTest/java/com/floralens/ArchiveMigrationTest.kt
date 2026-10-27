package com.floralens

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.db.FloraDatabase
import com.floralens.data.photos.FilePhotoStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import java.util.UUID

class ArchiveMigrationTest {
    @Test fun v2Upgrade_preservesRowsCareLocationsPhotos_andBackfillsCoverWithoutDuplicates() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val store = FilePhotoStore(context)
        val uri = store.save(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.floralens.data.db.FloraDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Only v2 fixture creation")
            }).build())
        try {
            helper.writableDatabase.apply {
                execSQL("INSERT INTO plants VALUES (10,'旧桂花','Osmanthus fragrans','桂花',?,12,1234,1,1000)", arrayOf(uri))
                execSQL("INSERT INTO plants VALUES (11,'旧手动','未知植物','未知植物',?,8,NULL,0,2000)", arrayOf(uri))
                execSQL("INSERT INTO observations VALUES (21,10,'IDENTIFIED','旧备注',?,31.2,121.4,1001)", arrayOf(uri))
                execSQL("INSERT INTO observations VALUES (22,10,'WATERED','养护',NULL,NULL,NULL,1234)")
                execSQL("INSERT INTO plant_info VALUES ('Osmanthus fragrans','桂花','{}','[]',NULL,99)")
            }
            helper.close()
            fun open() = Room.databaseBuilder(context, FloraDatabase::class.java, name)
                .addMigrations(FloraDatabase.MIGRATION_1_2, FloraDatabase.MIGRATION_2_3).build()
            open().use { db ->
                val old = db.archiveDao().plant(10)!!
                assertEquals("scientific:osmanthus fragrans", old.speciesKey)
                assertEquals(21L, old.coverObservationId)
                assertEquals(12, old.waterIntervalDays); assertEquals(1234L, old.lastWateredAtEpochMs)
                assertTrue(old.remindersEnabled); assertEquals(uri, old.photoUri)
                assertEquals(2, db.archiveDao().history(10).size)
                val observed = db.archiveDao().observation(21)!!
                assertEquals("旧备注", observed.note); assertEquals(31.2, observed.latitude!!, 0.0)
                assertEquals(121.4, observed.longitude!!, 0.0); assertEquals(1001L, observed.timestampEpochMs)
                assertEquals("WATERED", db.archiveDao().observation(22)!!.type)
                val manual = db.archiveDao().plant(11)!!
                assertTrue(manual.speciesKey.startsWith("unknown:")); assertFalse(manual.remindersEnabled)
                assertEquals(1, db.archiveDao().history(11).size)
                assertEquals("PHOTO_OBSERVATION", db.archiveDao().observation(manual.coverObservationId!!)!!.type)
                assertTrue(File(URI(uri)).canRead())
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM plant_info").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
                db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            open().use { db -> assertEquals(2, db.archiveDao().history(10).size); assertEquals(1, db.archiveDao().history(11).size) }
        } finally { helper.close(); context.deleteDatabase(name); store.delete(uri) }
    }
}

private inline fun <T> FloraDatabase.use(block: (FloraDatabase) -> T): T = try { block(this) } finally { close() }
