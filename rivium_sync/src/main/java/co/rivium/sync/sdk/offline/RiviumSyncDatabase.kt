package co.rivium.sync.sdk.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Type converters for Room database
 */
class RiviumSyncTypeConverters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromOperationType(type: OperationType): String = type.name

    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)
}

/**
 * Room database for RiviumSync offline storage
 */
@Database(
    entities = [CachedDocument::class, PendingOperation::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(RiviumSyncTypeConverters::class)
abstract class RiviumSyncDatabase : RoomDatabase() {

    abstract fun riviumSyncDao(): RiviumSyncDao

    companion object {
        private const val DATABASE_NAME = "rivium_sync_offline.db"

        @Volatile
        private var INSTANCE: RiviumSyncDatabase? = null

        fun getInstance(context: Context): RiviumSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RiviumSyncDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                RiviumSyncDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Close the database instance
         */
        fun close() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
