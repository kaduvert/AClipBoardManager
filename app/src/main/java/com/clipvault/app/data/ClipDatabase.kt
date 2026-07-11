package com.clipvault.app.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(entities = [ClipEntry::class], version = 1, exportSchema = false)
abstract class ClipDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var instance: ClipDatabase? = null

        fun get(context: Context): ClipDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipDatabase::class.java,
                    "clipvault.db"
                )
                    .setDriver(AndroidSQLiteDriver())
                    .build()
                    .also { instance = it }
            }
    }
}
