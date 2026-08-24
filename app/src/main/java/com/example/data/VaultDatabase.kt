package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vaultlock_secure.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
