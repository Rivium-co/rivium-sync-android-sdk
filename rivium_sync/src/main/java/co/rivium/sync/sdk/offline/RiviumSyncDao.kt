package co.rivium.sync.sdk.offline

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RiviumSync offline storage
 */
@Dao
interface RiviumSyncDao {

    // ==================== Document Operations ====================

    /**
     * Insert or update a document
     */
    @Upsert
    suspend fun upsertDocument(document: CachedDocument)

    /**
     * Insert or update multiple documents
     */
    @Upsert
    suspend fun upsertDocuments(documents: List<CachedDocument>)

    /**
     * Get a document by ID
     */
    @Query("SELECT * FROM sync_documents WHERE id = :documentId")
    suspend fun getDocument(documentId: String): CachedDocument?

    /**
     * Get a document by ID as Flow (for real-time updates)
     */
    @Query("SELECT * FROM sync_documents WHERE id = :documentId")
    fun getDocumentFlow(documentId: String): Flow<CachedDocument?>

    /**
     * Get all documents in a collection
     */
    @Query("""
        SELECT * FROM sync_documents
        WHERE databaseId = :databaseId AND collectionId = :collectionId
        AND syncStatus != 'PENDING_DELETE'
        ORDER BY updatedAt DESC
    """)
    suspend fun getDocuments(databaseId: String, collectionId: String): List<CachedDocument>

    /**
     * Get all documents in a collection as Flow
     */
    @Query("""
        SELECT * FROM sync_documents
        WHERE databaseId = :databaseId AND collectionId = :collectionId
        AND syncStatus != 'PENDING_DELETE'
        ORDER BY updatedAt DESC
    """)
    fun getDocumentsFlow(databaseId: String, collectionId: String): Flow<List<CachedDocument>>

    /**
     * Delete a document by ID
     */
    @Query("DELETE FROM sync_documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)

    /**
     * Delete all documents in a collection
     */
    @Query("DELETE FROM sync_documents WHERE databaseId = :databaseId AND collectionId = :collectionId")
    suspend fun deleteCollection(databaseId: String, collectionId: String)

    /**
     * Delete all documents in a database
     */
    @Query("DELETE FROM sync_documents WHERE databaseId = :databaseId")
    suspend fun deleteDatabase(databaseId: String)

    /**
     * Get documents with pending sync status
     */
    @Query("""
        SELECT * FROM sync_documents
        WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'SYNC_FAILED')
        ORDER BY localUpdatedAt ASC
    """)
    suspend fun getPendingDocuments(): List<CachedDocument>

    /**
     * Update sync status for a document
     */
    @Query("UPDATE sync_documents SET syncStatus = :status, lastError = :error WHERE id = :documentId")
    suspend fun updateSyncStatus(documentId: String, status: SyncStatus, error: String? = null)

    /**
     * Get count of pending operations
     */
    @Query("""
        SELECT COUNT(*) FROM sync_documents
        WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')
    """)
    suspend fun getPendingCount(): Int

    /**
     * Get count of pending operations as Flow
     */
    @Query("""
        SELECT COUNT(*) FROM sync_documents
        WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')
    """)
    fun getPendingCountFlow(): Flow<Int>

    // ==================== Pending Operations Queue ====================

    /**
     * Insert a pending operation
     */
    @Insert
    suspend fun insertOperation(operation: PendingOperation): Long

    /**
     * Update a pending operation
     */
    @Update
    suspend fun updateOperation(operation: PendingOperation)

    /**
     * Delete a pending operation
     */
    @Delete
    suspend fun deleteOperation(operation: PendingOperation)

    /**
     * Delete a pending operation by ID
     */
    @Query("DELETE FROM pending_operations WHERE operationId = :operationId")
    suspend fun deleteOperationById(operationId: Long)

    /**
     * Get all pending operations ordered by creation time
     */
    @Query("SELECT * FROM pending_operations WHERE processing = 0 ORDER BY createdAt ASC")
    suspend fun getPendingOperations(): List<PendingOperation>

    /**
     * Get pending operations for a specific document
     */
    @Query("SELECT * FROM pending_operations WHERE documentId = :documentId ORDER BY createdAt ASC")
    suspend fun getOperationsForDocument(documentId: String): List<PendingOperation>

    /**
     * Mark operation as processing
     */
    @Query("UPDATE pending_operations SET processing = 1 WHERE operationId = :operationId")
    suspend fun markOperationProcessing(operationId: Long)

    /**
     * Mark operation as failed with retry
     */
    @Query("""
        UPDATE pending_operations
        SET processing = 0, retryCount = retryCount + 1, lastError = :error
        WHERE operationId = :operationId
    """)
    suspend fun markOperationFailed(operationId: Long, error: String)

    /**
     * Delete all operations for a document
     */
    @Query("DELETE FROM pending_operations WHERE documentId = :documentId")
    suspend fun deleteOperationsForDocument(documentId: String)

    /**
     * Get operations that have exceeded max retries
     */
    @Query("SELECT * FROM pending_operations WHERE retryCount >= :maxRetries")
    suspend fun getFailedOperations(maxRetries: Int): List<PendingOperation>

    /**
     * Clear all pending operations
     */
    @Query("DELETE FROM pending_operations")
    suspend fun clearAllOperations()

    // ==================== Cache Management ====================

    /**
     * Clear all cached documents
     */
    @Query("DELETE FROM sync_documents")
    suspend fun clearAllDocuments()

    /**
     * Get total cache size (number of documents)
     */
    @Query("SELECT COUNT(*) FROM sync_documents")
    suspend fun getCacheSize(): Int

    /**
     * Get oldest documents to evict when cache is full
     */
    @Query("""
        SELECT * FROM sync_documents
        WHERE syncStatus = 'SYNCED'
        ORDER BY localUpdatedAt ASC
        LIMIT :limit
    """)
    suspend fun getOldestSyncedDocuments(limit: Int): List<CachedDocument>

    /**
     * Delete documents by IDs
     */
    @Query("DELETE FROM sync_documents WHERE id IN (:ids)")
    suspend fun deleteDocumentsByIds(ids: List<String>)
}
