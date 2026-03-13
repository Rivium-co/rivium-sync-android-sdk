package co.rivium.sync.sdk.offline

import co.rivium.sync.sdk.SyncDocument

/**
 * Conflict resolution strategy
 */
enum class ConflictStrategy {
    /**
     * Server version always wins
     */
    SERVER_WINS,

    /**
     * Client version always wins (force update)
     */
    CLIENT_WINS,

    /**
     * Let the app decide via callback
     */
    MANUAL,

    /**
     * Auto-merge non-conflicting fields
     */
    MERGE
}

/**
 * Result of a conflict
 */
sealed class ConflictResult {
    /**
     * No conflict occurred
     */
    data class NoConflict(val document: SyncDocument) : ConflictResult()

    /**
     * Server version was used
     */
    data class ServerWins(val serverDocument: SyncDocument) : ConflictResult()

    /**
     * Client version was used (forced update)
     */
    data class ClientWins(val clientDocument: SyncDocument) : ConflictResult()

    /**
     * Documents were merged
     */
    data class Merged(val mergedDocument: SyncDocument) : ConflictResult()

    /**
     * Conflict needs manual resolution
     */
    data class NeedsResolution(
        val documentId: String,
        val localData: Map<String, Any?>,
        val serverData: Map<String, Any?>,
        val localVersion: Int,
        val serverVersion: Int
    ) : ConflictResult()
}

/**
 * Conflict information for manual resolution
 */
data class ConflictInfo(
    val documentId: String,
    val databaseId: String,
    val collectionId: String,
    val localData: Map<String, Any?>,
    val serverData: Map<String, Any?>,
    val localVersion: Int,
    val serverVersion: Int
)

/**
 * User's choice for resolving a conflict
 */
enum class ConflictChoice {
    USE_LOCAL,
    USE_SERVER,
    USE_MERGED
}

/**
 * Callback for manual conflict resolution
 */
fun interface ConflictResolver {
    /**
     * Called when a conflict needs manual resolution
     *
     * @param conflict The conflict information
     * @return The user's choice and optionally merged data
     */
    fun resolve(conflict: ConflictInfo): Pair<ConflictChoice, Map<String, Any?>?>
}
