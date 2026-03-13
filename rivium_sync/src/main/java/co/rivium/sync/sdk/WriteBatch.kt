package co.rivium.sync.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A write batch is used to perform multiple writes as a single atomic unit.
 *
 * A WriteBatch object can be acquired by calling `RiviumSync.batch()`. It provides
 * methods for adding writes to the batch. None of the writes will be committed
 * (or visible locally) until `commit()` is called.
 *
 * Unlike transactions, write batches are persisted offline and therefore are
 * preferable when you don't need to condition your writes on read data.
 *
 * Usage:
 * ```kotlin
 * val batch = rivium_sync.batch()
 *
 * // Set a document
 * batch.set(usersCollection, "user1", mapOf("name" to "John", "age" to 30))
 *
 * // Update a document
 * batch.update(usersCollection, "user2", mapOf("status" to "active"))
 *
 * // Delete a document
 * batch.delete(usersCollection, "user3")
 *
 * // Commit the batch
 * batch.commit()
 * ```
 */
class WriteBatch internal constructor(
    private val apiClient: ApiClient
) {
    private val operations = mutableListOf<BatchOperation>()
    private var committed = false

    /**
     * Represents a single operation in the batch
     */
    internal sealed class BatchOperation {
        data class Set(
            val databaseId: String,
            val collectionId: String,
            val documentId: String,
            val data: Map<String, Any?>
        ) : BatchOperation()

        data class Update(
            val databaseId: String,
            val collectionId: String,
            val documentId: String,
            val data: Map<String, Any?>
        ) : BatchOperation()

        data class Delete(
            val databaseId: String,
            val collectionId: String,
            val documentId: String
        ) : BatchOperation()

        data class Create(
            val databaseId: String,
            val collectionId: String,
            val data: Map<String, Any?>
        ) : BatchOperation()
    }

    /**
     * Writes to the document referred to by the provided collection and document ID.
     * If the document does not exist yet, it will be created.
     * If the document exists, its contents will be overwritten.
     *
     * @param collection The collection containing the document
     * @param documentId The ID of the document to write
     * @param data The data to write to the document
     * @return This WriteBatch instance for chaining
     */
    fun set(collection: SyncCollection, documentId: String, data: Map<String, Any?>): WriteBatch {
        checkNotCommitted()
        operations.add(BatchOperation.Set(collection.databaseId, collection.id, documentId, data))
        return this
    }

    /**
     * Updates fields in the document referred to by the provided collection and document ID.
     * The document must exist. Fields not specified in the update are not modified.
     *
     * @param collection The collection containing the document
     * @param documentId The ID of the document to update
     * @param data The fields to update
     * @return This WriteBatch instance for chaining
     */
    fun update(collection: SyncCollection, documentId: String, data: Map<String, Any?>): WriteBatch {
        checkNotCommitted()
        operations.add(BatchOperation.Update(collection.databaseId, collection.id, documentId, data))
        return this
    }

    /**
     * Deletes the document referred to by the provided collection and document ID.
     *
     * @param collection The collection containing the document
     * @param documentId The ID of the document to delete
     * @return This WriteBatch instance for chaining
     */
    fun delete(collection: SyncCollection, documentId: String): WriteBatch {
        checkNotCommitted()
        operations.add(BatchOperation.Delete(collection.databaseId, collection.id, documentId))
        return this
    }

    /**
     * Creates a new document with an auto-generated ID in the specified collection.
     *
     * @param collection The collection to create the document in
     * @param data The data for the new document
     * @return This WriteBatch instance for chaining
     */
    fun create(collection: SyncCollection, data: Map<String, Any?>): WriteBatch {
        checkNotCommitted()
        operations.add(BatchOperation.Create(collection.databaseId, collection.id, data))
        return this
    }

    /**
     * Commits all of the writes in this write batch as a single atomic unit.
     *
     * @throws RiviumSyncException.BatchWriteException if the batch commit fails
     * @throws IllegalStateException if the batch has already been committed
     */
    suspend fun commit() {
        checkNotCommitted()
        committed = true

        if (operations.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                apiClient.executeBatch(operations.map { op ->
                    when (op) {
                        is BatchOperation.Set -> mapOf(
                            "type" to "set",
                            "databaseId" to op.databaseId,
                            "collectionId" to op.collectionId,
                            "documentId" to op.documentId,
                            "data" to op.data
                        )
                        is BatchOperation.Update -> mapOf(
                            "type" to "update",
                            "databaseId" to op.databaseId,
                            "collectionId" to op.collectionId,
                            "documentId" to op.documentId,
                            "data" to op.data
                        )
                        is BatchOperation.Delete -> mapOf(
                            "type" to "delete",
                            "databaseId" to op.databaseId,
                            "collectionId" to op.collectionId,
                            "documentId" to op.documentId
                        )
                        is BatchOperation.Create -> mapOf(
                            "type" to "create",
                            "databaseId" to op.databaseId,
                            "collectionId" to op.collectionId,
                            "data" to op.data
                        )
                    }
                })
            } catch (e: Exception) {
                committed = false // Allow retry
                throw RiviumSyncException.BatchWriteException("Batch commit failed: ${e.message}", e)
            }
        }
    }

    /**
     * Commits all of the writes in this write batch as a single atomic unit.
     * Callback-based version.
     *
     * @param onSuccess Called when the batch commits successfully
     * @param onError Called if the batch commit fails
     */
    fun commit(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                commit()
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Returns the number of operations in this batch
     */
    val size: Int
        get() = operations.size

    /**
     * Returns true if this batch has no operations
     */
    val isEmpty: Boolean
        get() = operations.isEmpty()

    private fun checkNotCommitted() {
        if (committed) {
            throw IllegalStateException("WriteBatch has already been committed")
        }
    }
}

