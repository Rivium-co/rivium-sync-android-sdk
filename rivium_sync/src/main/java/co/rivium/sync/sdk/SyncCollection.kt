package co.rivium.sync.sdk

/**
 * Represents a collection in a RiviumSync database
 */
interface SyncCollection {
    val id: String
    val name: String
    val databaseId: String

    // ==================== CRUD Operations ====================

    /**
     * Add a new document to the collection
     * @return The created document with generated ID
     */
    suspend fun add(data: Map<String, Any?>): SyncDocument

    /**
     * Get a document by ID
     */
    suspend fun get(documentId: String): SyncDocument?

    /**
     * Get all documents in the collection
     */
    suspend fun getAll(): List<SyncDocument>

    /**
     * Update a document
     * @param documentId Document ID
     * @param data Fields to update (partial update)
     */
    suspend fun update(documentId: String, data: Map<String, Any?>): SyncDocument

    /**
     * Set a document (replace entire data)
     * @param documentId Document ID
     * @param data Complete document data
     */
    suspend fun set(documentId: String, data: Map<String, Any?>): SyncDocument

    /**
     * Delete a document
     */
    suspend fun delete(documentId: String)

    // ==================== Query Operations ====================

    /**
     * Create a query builder
     */
    fun query(): SyncQuery

    /**
     * Query with a where clause
     */
    fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery

    // ==================== Realtime Listeners ====================

    /**
     * Listen to all changes in the collection
     * @return Listener registration that can be used to unsubscribe
     */
    fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration

    /**
     * Listen to changes in a specific document
     */
    fun listenDocument(documentId: String, callback: (SyncDocument?) -> Unit): ListenerRegistration

    // ==================== Callback-based operations ====================

    fun add(data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit)
    fun get(documentId: String, onSuccess: (SyncDocument?) -> Unit, onError: (Throwable) -> Unit)
    fun getAll(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit)
    fun update(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit)
    fun set(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit)
    fun delete(documentId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit)
}

/**
 * Listener registration for unsubscribing
 */
interface ListenerRegistration {
    fun remove()
}

/**
 * Query builder for collections
 */
interface SyncQuery {
    fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery
    fun orderBy(field: String, direction: OrderDirection = OrderDirection.ASCENDING): SyncQuery
    fun limit(count: Int): SyncQuery
    fun offset(count: Int): SyncQuery

    suspend fun get(): List<SyncDocument>
    fun get(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit)
    fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration
}

/**
 * Query operators
 */
enum class QueryOperator(val value: String) {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    ARRAY_CONTAINS("array-contains"),
    IN("in"),
    NOT_IN("not-in")
}

/**
 * Order direction for queries
 */
enum class OrderDirection(val value: String) {
    ASCENDING("asc"),
    DESCENDING("desc")
}
