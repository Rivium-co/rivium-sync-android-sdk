package co.rivium.sync.sdk.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Types of pending operations
 */
enum class OperationType {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Room entity for pending operations queue
 *
 * Stores operations that need to be synced to the server
 */
@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["databaseId", "collectionId"]),
        Index(value = ["createdAt"])
    ]
)
data class PendingOperation(
    @PrimaryKey(autoGenerate = true)
    val operationId: Long = 0,

    /**
     * Document ID
     */
    val documentId: String,

    /**
     * Database ID
     */
    val databaseId: String,

    /**
     * Collection ID
     */
    val collectionId: String,

    /**
     * Type of operation
     */
    val operationType: OperationType,

    /**
     * Document data as JSON (for CREATE and UPDATE)
     */
    val dataJson: String?,

    /**
     * Base version for conflict detection
     */
    val baseVersion: Int?,

    /**
     * When the operation was created
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Number of retry attempts
     */
    val retryCount: Int = 0,

    /**
     * Last error message
     */
    val lastError: String? = null,

    /**
     * Whether this operation is currently being processed
     */
    val processing: Boolean = false
) {
    companion object {
        private val gson = Gson()
        private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

        fun create(
            documentId: String,
            databaseId: String,
            collectionId: String,
            data: Map<String, Any?>
        ): PendingOperation {
            return PendingOperation(
                documentId = documentId,
                databaseId = databaseId,
                collectionId = collectionId,
                operationType = OperationType.CREATE,
                dataJson = gson.toJson(data),
                baseVersion = null
            )
        }

        fun update(
            documentId: String,
            databaseId: String,
            collectionId: String,
            data: Map<String, Any?>,
            baseVersion: Int
        ): PendingOperation {
            return PendingOperation(
                documentId = documentId,
                databaseId = databaseId,
                collectionId = collectionId,
                operationType = OperationType.UPDATE,
                dataJson = gson.toJson(data),
                baseVersion = baseVersion
            )
        }

        fun delete(
            documentId: String,
            databaseId: String,
            collectionId: String,
            baseVersion: Int
        ): PendingOperation {
            return PendingOperation(
                documentId = documentId,
                databaseId = databaseId,
                collectionId = collectionId,
                operationType = OperationType.DELETE,
                dataJson = null,
                baseVersion = baseVersion
            )
        }
    }

    /**
     * Get data as a map
     */
    fun getData(): Map<String, Any?>? {
        return dataJson?.let {
            try {
                gson.fromJson(it, mapType)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Create a copy with incremented retry count
     */
    fun withRetry(error: String): PendingOperation {
        return copy(
            retryCount = retryCount + 1,
            lastError = error,
            processing = false
        )
    }

    /**
     * Mark as processing
     */
    fun markProcessing(): PendingOperation {
        return copy(processing = true)
    }
}
