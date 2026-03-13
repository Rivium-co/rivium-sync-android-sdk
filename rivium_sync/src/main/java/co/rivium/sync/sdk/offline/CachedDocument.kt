package co.rivium.sync.sdk.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import co.rivium.sync.sdk.SyncDocument

/**
 * Room entity for cached documents
 *
 * Stores documents locally for offline support
 */
@Entity(
    tableName = "sync_documents",
    indices = [
        Index(value = ["databaseId", "collectionId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"])
    ]
)
data class CachedDocument(
    @PrimaryKey
    val id: String,

    /**
     * Database ID this document belongs to
     */
    val databaseId: String,

    /**
     * Collection ID this document belongs to
     */
    val collectionId: String,

    /**
     * Document data as JSON string
     */
    val dataJson: String,

    /**
     * Creation timestamp (milliseconds since epoch)
     */
    val createdAt: Long,

    /**
     * Last update timestamp (milliseconds since epoch)
     */
    val updatedAt: Long,

    /**
     * Document version for conflict detection
     */
    val version: Int,

    /**
     * Sync status
     */
    val syncStatus: SyncStatus,

    /**
     * Base version when the local change was made (for conflict detection)
     */
    val baseVersion: Int? = null,

    /**
     * Number of sync retry attempts
     */
    val retryCount: Int = 0,

    /**
     * Last sync error message (if any)
     */
    val lastError: String? = null,

    /**
     * Timestamp when this cache entry was last modified locally
     */
    val localUpdatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()
        private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

        /**
         * Create a CachedDocument from a SyncDocument
         */
        fun fromSyncDocument(
            doc: SyncDocument,
            databaseId: String,
            collectionId: String,
            syncStatus: SyncStatus,
            baseVersion: Int? = null
        ): CachedDocument {
            return CachedDocument(
                id = doc.id,
                databaseId = databaseId,
                collectionId = collectionId,
                dataJson = gson.toJson(doc.data),
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
                version = doc.version,
                syncStatus = syncStatus,
                baseVersion = baseVersion
            )
        }
    }

    /**
     * Convert to SyncDocument
     */
    fun toSyncDocument(): SyncDocument {
        val data: Map<String, Any?> = try {
            gson.fromJson(dataJson, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        return SyncDocument(
            id = id,
            data = data,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version
        )
    }

    /**
     * Get the data as a map
     */
    fun getData(): Map<String, Any?> {
        return try {
            gson.fromJson(dataJson, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Create a copy with updated sync status
     */
    fun withSyncStatus(status: SyncStatus, error: String? = null): CachedDocument {
        return copy(
            syncStatus = status,
            lastError = error,
            retryCount = if (status == SyncStatus.SYNC_FAILED) retryCount + 1 else 0,
            localUpdatedAt = System.currentTimeMillis()
        )
    }
}
