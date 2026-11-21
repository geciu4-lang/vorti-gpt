package com.vortix.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vortix.app.data.local.dao.BatterySampleDao
import com.vortix.app.data.local.dao.ChargingProfileDao
import com.vortix.app.data.local.entity.BatterySampleEntity
import com.vortix.app.data.local.entity.ChargingProfileEntity

@Database(
    entities = [BatterySampleEntity::class, ChargingProfileEntity::class],
    version = 3,
    exportSchema = true
)
abstract class VortixDatabase : RoomDatabase() {
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun chargingProfileDao(): ChargingProfileDao
}


/** Adds context fields without discarding previously collected battery history. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE battery_samples ADD COLUMN screenInteractive INTEGER")
        database.execSQL("ALTER TABLE battery_samples ADD COLUMN thermalStatus INTEGER")
        database.execSQL("ALTER TABLE battery_samples ADD COLUMN powerSaveMode INTEGER")
        database.execSQL("ALTER TABLE battery_samples ADD COLUMN deviceIdleMode INTEGER")
        database.execSQL("ALTER TABLE battery_samples ADD COLUMN dischargePredictionMillis INTEGER")
    }
}


/** Stores named adapter sessions without modifying or deleting existing battery history. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `charging_profiles` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `startedAtEpochMillis` INTEGER NOT NULL,
                `endedAtEpochMillis` INTEGER,
                `sampleCount` INTEGER NOT NULL,
                `totalPowerWatts` REAL NOT NULL,
                `averagePowerWatts` REAL,
                `peakPowerWatts` REAL NOT NULL,
                `startTemperatureCelsius` REAL,
                `endTemperatureCelsius` REAL,
                `temperatureDeltaCelsius` REAL,
                `efficiencyGrade` TEXT
            )""".trimIndent()
        )
    }
}
