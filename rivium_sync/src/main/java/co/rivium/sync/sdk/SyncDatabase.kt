package co.rivium.sync.sdk

/**
 * Represents a RiviumSync database
 */
interface SyncDatabase {
    /**
     * The identifier this database is addressed by. For a reference obtained
     * with [RiviumSync.database] this is the database NAME you passed.
     */
    val id: String
    val name: String

    /**
     * Get a collection reference by its NAME.
     *
     * @param collectionIdOrName The collection NAME exactly as shown in Rivium
     * Console (for example `"todos"`), not its UUID. Realtime updates are
     * published per collection name, so [SyncCollection.listen] only receives
     * changes when the collection is addressed by name; a UUID still works for
     * plain reads and writes but live updates never arrive.
     */
    fun collection(collectionIdOrName: String): SyncCollection

    /**
     * List all collections in this database
     */
    suspend fun listCollections(): List<CollectionInfo>

    /**
     * Create a new collection in this database
     */
    suspend fun createCollection(name: String): SyncCollection

    /**
     * Delete a collection
     */
    suspend fun deleteCollection(collectionId: String)
}
