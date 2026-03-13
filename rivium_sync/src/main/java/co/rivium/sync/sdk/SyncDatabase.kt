package co.rivium.sync.sdk

/**
 * Represents a RiviumSync database
 */
interface SyncDatabase {
    val id: String
    val name: String

    /**
     * Get a collection reference by ID or name
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
