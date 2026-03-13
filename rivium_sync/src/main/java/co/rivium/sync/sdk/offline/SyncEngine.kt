package co.rivium.sync.sdk.offline

import co.rivium.sync.sdk.ApiClient
import co.rivium.sync.sdk.SyncDocument
import co.rivium.sync.sdk.RiviumSyncLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sync state
 */
enum class SyncState {
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR
}

/**
 * Sync engine handles synchronization between local cache and server
 */
internal class SyncEngine(
    private val apiClient: ApiClient,
    private val localStore: LocalStorageManager,
    private val conflictStrategy: ConflictStrategy = ConflictStrategy.SERVER_WINS,
    private val conflictResolver: ConflictResolver? = null,
    private val maxRetries: Int = 3
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var syncJob: Job? = null
    private var pendingCountJob: Job? = null
    private val syncListeners = mutableListOf<co.rivium.sync.sdk.SyncListener>()

    init {
        // Observe pending count from database in real-time
        pendingCountJob = scope.launch {
            localStore.getPendingCountFlow().collect { count ->
                _pendingCount.value = count
                RiviumSyncLogger.d("SyncEngine: Pending count updated to $count")
            }
        }
    }

    /**
     * Add a sync listener
     */
    fun addSyncListener(listener: co.rivium.sync.sdk.SyncListener) {
        syncListeners.add(listener)
    }

    /**
     * Remove a sync listener
     */
    fun removeSyncListener(listener: co.rivium.sync.sdk.SyncListener) {
        syncListeners.remove(listener)
    }

    /**
     * Called when connection state changes
     */
    fun onConnectionStateChanged(connected: Boolean) {
        _isOnline.value = connected
        RiviumSyncLogger.i("SyncEngine: Connection state changed to $connected")

        if (connected) {
            _syncState.value = SyncState.IDLE
            // Trigger sync when coming online
            syncPendingOperations()
        } else {
            _syncState.value = SyncState.OFFLINE
        }
    }

    /**
     * Sync all pending operations
     */
    fun syncPendingOperations() {
        if (!_isOnline.value) {
            RiviumSyncLogger.d("SyncEngine: Cannot sync, offline")
            return
        }

        if (syncJob?.isActive == true) {
            RiviumSyncLogger.d("SyncEngine: Sync already in progress")
            return
        }

        syncJob = scope.launch {
            try {
                _syncState.value = SyncState.SYNCING
                syncListeners.forEach { it.onSyncStarted() }

                val pending = localStore.getPendingDocuments()
                _pendingCount.value = pending.size

                if (pending.isEmpty()) {
                    RiviumSyncLogger.d("SyncEngine: No pending operations")
                    _syncState.value = SyncState.IDLE
                    syncListeners.forEach { it.onSyncCompleted(0) }
                    return@launch
                }

                RiviumSyncLogger.i("SyncEngine: Syncing ${pending.size} pending operations")
                var syncedCount = 0

                for (doc in pending) {
                    try {
                        val result = syncDocument(doc)
                        if (result) {
                            syncedCount++
                            _pendingCount.value = _pendingCount.value - 1
                        }
                    } catch (e: Exception) {
                        RiviumSyncLogger.e("SyncEngine: Failed to sync document ${doc.id}", e)
                        // Mark as failed but continue with other documents
                        localStore.updateSyncStatus(doc.id, SyncStatus.SYNC_FAILED, e.message)
                    }
                }

                _syncState.value = SyncState.IDLE
                syncListeners.forEach { it.onSyncCompleted(syncedCount) }
                RiviumSyncLogger.i("SyncEngine: Sync completed, synced $syncedCount documents")

            } catch (e: Exception) {
                RiviumSyncLogger.e("SyncEngine: Sync failed", e)
                _syncState.value = SyncState.ERROR
                syncListeners.forEach { it.onSyncFailed(e) }
            }
        }
    }

    /**
     * Sync a single document
     */
    private suspend fun syncDocument(cached: CachedDocument): Boolean {
        return when (cached.syncStatus) {
            SyncStatus.PENDING_CREATE -> syncCreate(cached)
            SyncStatus.PENDING_UPDATE -> syncUpdate(cached)
            SyncStatus.PENDING_DELETE -> syncDelete(cached)
            SyncStatus.SYNC_FAILED -> retrySyncFailed(cached)
            else -> true // Already synced
        }
    }

    /**
     * Sync a create operation
     */
    private suspend fun syncCreate(cached: CachedDocument): Boolean {
        return try {
            val data = cached.getData()
            val serverDoc = apiClient.addDocument(
                cached.databaseId,
                cached.collectionId,
                data
            )

            // Update local cache with server response
            localStore.saveDocument(
                document = serverDoc,
                databaseId = cached.databaseId,
                collectionId = cached.collectionId,
                syncStatus = SyncStatus.SYNCED
            )

            // If the server assigned a different ID, delete the old local entry
            if (serverDoc.id != cached.id) {
                localStore.deleteDocument(cached.id)
            }

            syncListeners.forEach { it.onDocumentSynced(serverDoc.id, OperationType.CREATE) }
            RiviumSyncLogger.d("SyncEngine: Created document ${serverDoc.id}")
            true
        } catch (e: Exception) {
            RiviumSyncLogger.e("SyncEngine: Failed to create document ${cached.id}", e)
            localStore.updateSyncStatus(cached.id, SyncStatus.SYNC_FAILED, e.message)
            false
        }
    }

    /**
     * Sync an update operation with conflict detection
     */
    private suspend fun syncUpdate(cached: CachedDocument): Boolean {
        return try {
            // First, check for conflicts
            val serverDoc = apiClient.getDocument(
                cached.databaseId,
                cached.collectionId,
                cached.id
            )

            val baseVersion = cached.baseVersion ?: cached.version

            if (serverDoc != null && serverDoc.version != baseVersion) {
                // Conflict detected!
                val result = handleConflict(cached, serverDoc)
                return result
            }

            // No conflict, proceed with update
            val data = cached.getData()
            val updatedDoc = apiClient.updateDocument(
                cached.databaseId,
                cached.collectionId,
                cached.id,
                data
            )

            localStore.saveDocument(
                document = updatedDoc,
                databaseId = cached.databaseId,
                collectionId = cached.collectionId,
                syncStatus = SyncStatus.SYNCED
            )

            syncListeners.forEach { it.onDocumentSynced(cached.id, OperationType.UPDATE) }
            RiviumSyncLogger.d("SyncEngine: Updated document ${cached.id}")
            true
        } catch (e: Exception) {
            RiviumSyncLogger.e("SyncEngine: Failed to update document ${cached.id}", e)
            localStore.updateSyncStatus(cached.id, SyncStatus.SYNC_FAILED, e.message)
            false
        }
    }

    /**
     * Handle a conflict between local and server versions
     */
    private suspend fun handleConflict(local: CachedDocument, server: SyncDocument): Boolean {
        val conflictInfo = ConflictInfo(
            documentId = local.id,
            databaseId = local.databaseId,
            collectionId = local.collectionId,
            localData = local.getData(),
            serverData = server.data,
            localVersion = local.version,
            serverVersion = server.version
        )

        syncListeners.forEach { it.onConflictDetected(conflictInfo) }
        RiviumSyncLogger.w("SyncEngine: Conflict detected for document ${local.id}")

        return when (conflictStrategy) {
            ConflictStrategy.SERVER_WINS -> {
                // Use server version
                localStore.saveDocument(
                    document = server,
                    databaseId = local.databaseId,
                    collectionId = local.collectionId,
                    syncStatus = SyncStatus.SYNCED
                )
                RiviumSyncLogger.d("SyncEngine: Conflict resolved - server wins")
                true
            }

            ConflictStrategy.CLIENT_WINS -> {
                // Force update server with local version
                val updated = apiClient.setDocument(
                    local.databaseId,
                    local.collectionId,
                    local.id,
                    local.getData()
                )
                localStore.saveDocument(
                    document = updated,
                    databaseId = local.databaseId,
                    collectionId = local.collectionId,
                    syncStatus = SyncStatus.SYNCED
                )
                RiviumSyncLogger.d("SyncEngine: Conflict resolved - client wins")
                true
            }

            ConflictStrategy.MERGE -> {
                // Auto-merge non-conflicting fields
                val merged = mergeData(local.getData(), server.data)
                val updated = apiClient.setDocument(
                    local.databaseId,
                    local.collectionId,
                    local.id,
                    merged
                )
                localStore.saveDocument(
                    document = updated,
                    databaseId = local.databaseId,
                    collectionId = local.collectionId,
                    syncStatus = SyncStatus.SYNCED
                )
                RiviumSyncLogger.d("SyncEngine: Conflict resolved - merged")
                true
            }

            ConflictStrategy.MANUAL -> {
                // Let the app decide
                val resolver = conflictResolver
                if (resolver != null) {
                    val (choice, mergedData) = resolver.resolve(conflictInfo)
                    when (choice) {
                        ConflictChoice.USE_LOCAL -> {
                            val updated = apiClient.setDocument(
                                local.databaseId,
                                local.collectionId,
                                local.id,
                                local.getData()
                            )
                            localStore.saveDocument(
                                document = updated,
                                databaseId = local.databaseId,
                                collectionId = local.collectionId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        }
                        ConflictChoice.USE_SERVER -> {
                            localStore.saveDocument(
                                document = server,
                                databaseId = local.databaseId,
                                collectionId = local.collectionId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        }
                        ConflictChoice.USE_MERGED -> {
                            val data = mergedData ?: mergeData(local.getData(), server.data)
                            val updated = apiClient.setDocument(
                                local.databaseId,
                                local.collectionId,
                                local.id,
                                data
                            )
                            localStore.saveDocument(
                                document = updated,
                                databaseId = local.databaseId,
                                collectionId = local.collectionId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        }
                    }
                    RiviumSyncLogger.d("SyncEngine: Conflict resolved - manual choice: $choice")
                    true
                } else {
                    // No resolver, default to server wins
                    localStore.saveDocument(
                        document = server,
                        databaseId = local.databaseId,
                        collectionId = local.collectionId,
                        syncStatus = SyncStatus.SYNCED
                    )
                    RiviumSyncLogger.d("SyncEngine: Conflict resolved - no resolver, server wins")
                    true
                }
            }
        }
    }

    /**
     * Merge two data maps (local changes win for conflicting fields, server data preserved for others)
     */
    private fun mergeData(local: Map<String, Any?>, server: Map<String, Any?>): Map<String, Any?> {
        val merged = server.toMutableMap()
        // Local changes override server for fields that were modified locally
        merged.putAll(local)
        return merged
    }

    /**
     * Sync a delete operation
     */
    private suspend fun syncDelete(cached: CachedDocument): Boolean {
        return try {
            apiClient.deleteDocument(
                cached.databaseId,
                cached.collectionId,
                cached.id
            )

            // Remove from local cache
            localStore.deleteDocument(cached.id)

            syncListeners.forEach { it.onDocumentSynced(cached.id, OperationType.DELETE) }
            RiviumSyncLogger.d("SyncEngine: Deleted document ${cached.id}")
            true
        } catch (e: Exception) {
            // If document not found on server, consider it deleted
            if (e.message?.contains("404") == true || e.message?.contains("not found") == true) {
                localStore.deleteDocument(cached.id)
                RiviumSyncLogger.d("SyncEngine: Document ${cached.id} already deleted on server")
                return true
            }

            RiviumSyncLogger.e("SyncEngine: Failed to delete document ${cached.id}", e)
            localStore.updateSyncStatus(cached.id, SyncStatus.SYNC_FAILED, e.message)
            false
        }
    }

    /**
     * Retry a failed sync operation
     */
    private suspend fun retrySyncFailed(cached: CachedDocument): Boolean {
        if (cached.retryCount >= maxRetries) {
            RiviumSyncLogger.w("SyncEngine: Max retries exceeded for document ${cached.id}")
            return false
        }

        // Determine the original operation type based on the document state
        return when {
            cached.baseVersion == null -> syncCreate(cached)
            else -> syncUpdate(cached)
        }
    }

    /**
     * Force sync now
     */
    fun forceSync() {
        syncJob?.cancel()
        syncPendingOperations()
    }

    /**
     * Cancel ongoing sync
     */
    fun cancelSync() {
        syncJob?.cancel()
        _syncState.value = SyncState.IDLE
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        syncJob?.cancel()
        pendingCountJob?.cancel()
        scope.cancel()
        syncListeners.clear()
    }
}
