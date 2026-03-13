package co.rivium.sync.sdk

import co.rivium.sync.sdk.offline.ConflictInfo
import co.rivium.sync.sdk.offline.OperationType

/**
 * Listener for sync events.
 * Implement this interface to monitor sync operations when offline persistence is enabled.
 */
interface SyncListener {
    /**
     * Called when sync operation starts
     */
    fun onSyncStarted()

    /**
     * Called when sync operation completes successfully
     * @param syncedCount Number of documents synced
     */
    fun onSyncCompleted(syncedCount: Int)

    /**
     * Called when sync operation fails
     * @param error The error that occurred
     */
    fun onSyncFailed(error: Throwable)

    /**
     * Called when a conflict is detected during sync
     * @param conflict Information about the conflict
     */
    fun onConflictDetected(conflict: ConflictInfo)

    /**
     * Called when a document is successfully synced
     * @param documentId ID of the synced document
     * @param operation Type of operation (CREATE, UPDATE, DELETE)
     */
    fun onDocumentSynced(documentId: String, operation: OperationType)
}
