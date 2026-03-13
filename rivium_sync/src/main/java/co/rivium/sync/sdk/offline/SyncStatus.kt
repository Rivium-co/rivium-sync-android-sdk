package co.rivium.sync.sdk.offline

/**
 * Sync status for cached documents
 */
enum class SyncStatus {
    /**
     * Document is synced with server
     */
    SYNCED,

    /**
     * Document was created locally and pending sync to server
     */
    PENDING_CREATE,

    /**
     * Document was updated locally and pending sync to server
     */
    PENDING_UPDATE,

    /**
     * Document was deleted locally and pending sync to server
     */
    PENDING_DELETE,

    /**
     * Document sync failed and needs retry
     */
    SYNC_FAILED
}
