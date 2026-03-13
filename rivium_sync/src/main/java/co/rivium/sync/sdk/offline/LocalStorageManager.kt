package co.rivium.sync.sdk.offline

import android.content.Context
import co.rivium.sync.sdk.SyncDocument
import co.rivium.sync.sdk.RiviumSyncLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages local storage for offline persistence
 */
class LocalStorageManager(context: Context) {

    private val database = RiviumSyncDatabase.getInstance(context)
    private val dao = database.riviumSyncDao()

    // ==================== Document Operations ====================

    /**
     * Save a document to local cache
     */
    suspend fun saveDocument(
        document: SyncDocument,
        databaseId: String,
        collectionId: String,
        syncStatus: SyncStatus,
        baseVersion: Int? = null
    ) {
        val cached = CachedDocument.fromSyncDocument(
            doc = document,
            databaseId = databaseId,
            collectionId = collectionId,
            syncStatus = syncStatus,
            baseVersion = baseVersion
        )
        dao.upsertDocument(cached)
        RiviumSyncLogger.d("LocalStorage: Saved document ${document.id} with status $syncStatus")
    }

    /**
     * Save multiple documents to local cache
     */
    suspend fun saveDocuments(
        documents: List<SyncDocument>,
        databaseId: String,
        collectionId: String,
        syncStatus: SyncStatus
    ) {
        val cached = documents.map { doc ->
            CachedDocument.fromSyncDocument(
                doc = doc,
                databaseId = databaseId,
                collectionId = collectionId,
                syncStatus = syncStatus
            )
        }
        dao.upsertDocuments(cached)
        RiviumSyncLogger.d("LocalStorage: Saved ${documents.size} documents")
    }

    /**
     * Get a document from local cache
     */
    suspend fun getDocument(documentId: String): SyncDocument? {
        return dao.getDocument(documentId)?.toSyncDocument()
    }

    /**
     * Get a document as Flow for real-time updates
     */
    fun getDocumentFlow(documentId: String): Flow<SyncDocument?> {
        return dao.getDocumentFlow(documentId).map { it?.toSyncDocument() }
    }

    /**
     * Get all documents in a collection from local cache
     */
    suspend fun getDocuments(databaseId: String, collectionId: String): List<SyncDocument> {
        return dao.getDocuments(databaseId, collectionId).map { it.toSyncDocument() }
    }

    /**
     * Get all documents in a collection as Flow
     */
    fun getDocumentsFlow(databaseId: String, collectionId: String): Flow<List<SyncDocument>> {
        return dao.getDocumentsFlow(databaseId, collectionId).map { list ->
            list.map { it.toSyncDocument() }
        }
    }

    /**
     * Delete a document from local cache
     */
    suspend fun deleteDocument(documentId: String) {
        dao.deleteDocument(documentId)
        RiviumSyncLogger.d("LocalStorage: Deleted document $documentId")
    }

    /**
     * Mark a document as pending delete
     */
    suspend fun markPendingDelete(
        documentId: String,
        databaseId: String,
        collectionId: String,
        baseVersion: Int
    ) {
        val existing = dao.getDocument(documentId)
        if (existing != null) {
            dao.upsertDocument(
                existing.copy(
                    syncStatus = SyncStatus.PENDING_DELETE,
                    baseVersion = baseVersion,
                    localUpdatedAt = System.currentTimeMillis()
                )
            )
        }
        RiviumSyncLogger.d("LocalStorage: Marked document $documentId for deletion")
    }

    /**
     * Update sync status for a document
     */
    suspend fun updateSyncStatus(documentId: String, status: SyncStatus, error: String? = null) {
        dao.updateSyncStatus(documentId, status, error)
        RiviumSyncLogger.d("LocalStorage: Updated sync status for $documentId to $status")
    }

    // ==================== Pending Operations ====================

    /**
     * Get all documents with pending sync status
     */
    suspend fun getPendingDocuments(): List<CachedDocument> {
        return dao.getPendingDocuments()
    }

    /**
     * Get count of pending operations
     */
    suspend fun getPendingCount(): Int {
        return dao.getPendingCount()
    }

    /**
     * Get pending count as Flow
     */
    fun getPendingCountFlow(): Flow<Int> {
        return dao.getPendingCountFlow()
    }

    // ==================== Operation Queue ====================

    /**
     * Queue a create operation
     */
    suspend fun queueCreate(
        documentId: String,
        databaseId: String,
        collectionId: String,
        data: Map<String, Any?>
    ) {
        val operation = PendingOperation.create(
            documentId = documentId,
            databaseId = databaseId,
            collectionId = collectionId,
            data = data
        )
        dao.insertOperation(operation)
        RiviumSyncLogger.d("LocalStorage: Queued CREATE operation for $documentId")
    }

    /**
     * Queue an update operation
     */
    suspend fun queueUpdate(
        documentId: String,
        databaseId: String,
        collectionId: String,
        data: Map<String, Any?>,
        baseVersion: Int
    ) {
        val operation = PendingOperation.update(
            documentId = documentId,
            databaseId = databaseId,
            collectionId = collectionId,
            data = data,
            baseVersion = baseVersion
        )
        dao.insertOperation(operation)
        RiviumSyncLogger.d("LocalStorage: Queued UPDATE operation for $documentId")
    }

    /**
     * Queue a delete operation
     */
    suspend fun queueDelete(
        documentId: String,
        databaseId: String,
        collectionId: String,
        baseVersion: Int
    ) {
        val operation = PendingOperation.delete(
            documentId = documentId,
            databaseId = databaseId,
            collectionId = collectionId,
            baseVersion = baseVersion
        )
        dao.insertOperation(operation)
        RiviumSyncLogger.d("LocalStorage: Queued DELETE operation for $documentId")
    }

    /**
     * Get pending operations
     */
    suspend fun getPendingOperations(): List<PendingOperation> {
        return dao.getPendingOperations()
    }

    /**
     * Mark operation as processing
     */
    suspend fun markOperationProcessing(operationId: Long) {
        dao.markOperationProcessing(operationId)
    }

    /**
     * Mark operation as failed
     */
    suspend fun markOperationFailed(operationId: Long, error: String) {
        dao.markOperationFailed(operationId, error)
    }

    /**
     * Delete completed operation
     */
    suspend fun deleteOperation(operationId: Long) {
        dao.deleteOperationById(operationId)
    }

    /**
     * Delete all operations for a document
     */
    suspend fun deleteOperationsForDocument(documentId: String) {
        dao.deleteOperationsForDocument(documentId)
    }

    // ==================== Cache Management ====================

    /**
     * Clear all local cache
     */
    suspend fun clearAll() {
        dao.clearAllDocuments()
        dao.clearAllOperations()
        RiviumSyncLogger.d("LocalStorage: Cleared all cache")
    }

    /**
     * Get cache size
     */
    suspend fun getCacheSize(): Int {
        return dao.getCacheSize()
    }

    /**
     * Evict old documents to make room for new ones
     */
    suspend fun evictOldDocuments(maxCacheSize: Int) {
        val currentSize = dao.getCacheSize()
        if (currentSize > maxCacheSize) {
            val toEvict = currentSize - maxCacheSize
            val oldDocs = dao.getOldestSyncedDocuments(toEvict)
            if (oldDocs.isNotEmpty()) {
                dao.deleteDocumentsByIds(oldDocs.map { it.id })
                RiviumSyncLogger.d("LocalStorage: Evicted ${oldDocs.size} old documents")
            }
        }
    }

    /**
     * Clear cache for a specific collection
     */
    suspend fun clearCollection(databaseId: String, collectionId: String) {
        dao.deleteCollection(databaseId, collectionId)
        RiviumSyncLogger.d("LocalStorage: Cleared collection $collectionId cache")
    }

    /**
     * Clear cache for a specific database
     */
    suspend fun clearDatabase(databaseId: String) {
        dao.deleteDatabase(databaseId)
        RiviumSyncLogger.d("LocalStorage: Cleared database $databaseId cache")
    }
}
