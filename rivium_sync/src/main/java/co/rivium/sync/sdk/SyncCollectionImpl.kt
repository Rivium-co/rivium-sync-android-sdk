package co.rivium.sync.sdk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import co.rivium.sync.sdk.offline.LocalStorageManager
import co.rivium.sync.sdk.offline.SyncEngine
import co.rivium.sync.sdk.offline.SyncStatus
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Implementation of SyncCollection interface
 */
internal class SyncCollectionImpl(
    override val id: String,
    override val name: String,
    override val databaseId: String,
    private val apiClient: ApiClient,
    private val mqttManager: MqttManager,
    private val localStorageManager: LocalStorageManager?,
    private val syncEngine: SyncEngine?
) : SyncCollection {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val isOfflineEnabled: Boolean
        get() = localStorageManager != null && syncEngine != null

    // ==================== CRUD Operations (Suspend) ====================

    override suspend fun add(data: Map<String, Any?>): SyncDocument {
        if (isOfflineEnabled) {
            return addWithOfflineSupport(data)
        }
        return apiClient.addDocument(databaseId, id, data)
    }

    private suspend fun addWithOfflineSupport(data: Map<String, Any?>): SyncDocument {
        val isOnline = syncEngine?.isOnline?.value ?: true

        if (isOnline) {
            // Online: Try server first
            return try {
                val doc = apiClient.addDocument(databaseId, id, data)
                // Cache the result
                localStorageManager?.saveDocument(
                    document = doc,
                    databaseId = databaseId,
                    collectionId = id,
                    syncStatus = SyncStatus.SYNCED
                )
                doc
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to add document online, saving offline: ${e.message}")
                // Fall back to offline
                createOfflineDocument(data)
            }
        } else {
            // Offline: Create optimistic document
            return createOfflineDocument(data)
        }
    }

    private suspend fun createOfflineDocument(data: Map<String, Any?>): SyncDocument {
        val tempId = "local_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val doc = SyncDocument(
            id = tempId,
            data = data,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        localStorageManager?.saveDocument(
            document = doc,
            databaseId = databaseId,
            collectionId = id,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        RiviumSyncLogger.d("Created offline document: $tempId")
        return doc
    }

    override suspend fun get(documentId: String): SyncDocument? {
        if (isOfflineEnabled) {
            return getWithOfflineSupport(documentId)
        }
        return apiClient.getDocument(databaseId, id, documentId)
    }

    private suspend fun getWithOfflineSupport(documentId: String): SyncDocument? {
        val isOnline = syncEngine?.isOnline?.value ?: true

        // Check local cache first
        val cached = localStorageManager?.getDocument(documentId)

        if (isOnline) {
            return try {
                val doc = apiClient.getDocument(databaseId, id, documentId)
                if (doc != null) {
                    // Update cache
                    localStorageManager?.saveDocument(
                        document = doc,
                        databaseId = databaseId,
                        collectionId = id,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
                doc
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to get document online, using cached: ${e.message}")
                cached
            }
        } else {
            return cached
        }
    }

    override suspend fun getAll(): List<SyncDocument> {
        if (isOfflineEnabled) {
            return getAllWithOfflineSupport()
        }
        return apiClient.getAllDocuments(databaseId, id)
    }

    private suspend fun getAllWithOfflineSupport(): List<SyncDocument> {
        val isOnline = syncEngine?.isOnline?.value ?: true

        if (isOnline) {
            return try {
                val docs = apiClient.getAllDocuments(databaseId, id)
                // Update cache
                localStorageManager?.saveDocuments(
                    documents = docs,
                    databaseId = databaseId,
                    collectionId = id,
                    syncStatus = SyncStatus.SYNCED
                )
                docs
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to get documents online, using cached: ${e.message}")
                localStorageManager?.getDocuments(databaseId, id) ?: emptyList()
            }
        } else {
            return localStorageManager?.getDocuments(databaseId, id) ?: emptyList()
        }
    }

    override suspend fun update(documentId: String, data: Map<String, Any?>): SyncDocument {
        if (isOfflineEnabled) {
            return updateWithOfflineSupport(documentId, data)
        }
        return apiClient.updateDocument(databaseId, id, documentId, data)
    }

    private suspend fun updateWithOfflineSupport(documentId: String, data: Map<String, Any?>): SyncDocument {
        val isOnline = syncEngine?.isOnline?.value ?: true

        // Get current version for conflict detection
        val existing = localStorageManager?.getDocument(documentId)
        val baseVersion = existing?.version ?: 1

        if (isOnline) {
            return try {
                val doc = apiClient.updateDocument(databaseId, id, documentId, data)
                // Update cache
                localStorageManager?.saveDocument(
                    document = doc,
                    databaseId = databaseId,
                    collectionId = id,
                    syncStatus = SyncStatus.SYNCED
                )
                doc
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to update document online, saving offline: ${e.message}")
                updateOfflineDocument(documentId, data, baseVersion)
            }
        } else {
            return updateOfflineDocument(documentId, data, baseVersion)
        }
    }

    private suspend fun updateOfflineDocument(documentId: String, data: Map<String, Any?>, baseVersion: Int): SyncDocument {
        val existing = localStorageManager?.getDocument(documentId)
        val now = System.currentTimeMillis()

        val doc = SyncDocument(
            id = documentId,
            data = data,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = baseVersion + 1
        )

        localStorageManager?.saveDocument(
            document = doc,
            databaseId = databaseId,
            collectionId = id,
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = baseVersion
        )

        RiviumSyncLogger.d("Updated offline document: $documentId")
        return doc
    }

    override suspend fun set(documentId: String, data: Map<String, Any?>): SyncDocument {
        if (isOfflineEnabled) {
            return setWithOfflineSupport(documentId, data)
        }
        return apiClient.setDocument(databaseId, id, documentId, data)
    }

    private suspend fun setWithOfflineSupport(documentId: String, data: Map<String, Any?>): SyncDocument {
        val isOnline = syncEngine?.isOnline?.value ?: true

        if (isOnline) {
            return try {
                val doc = apiClient.setDocument(databaseId, id, documentId, data)
                localStorageManager?.saveDocument(
                    document = doc,
                    databaseId = databaseId,
                    collectionId = id,
                    syncStatus = SyncStatus.SYNCED
                )
                doc
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to set document online, saving offline: ${e.message}")
                setOfflineDocument(documentId, data)
            }
        } else {
            return setOfflineDocument(documentId, data)
        }
    }

    private suspend fun setOfflineDocument(documentId: String, data: Map<String, Any?>): SyncDocument {
        val existing = localStorageManager?.getDocument(documentId)
        val now = System.currentTimeMillis()
        val isCreate = existing == null

        val doc = SyncDocument(
            id = documentId,
            data = data,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = (existing?.version ?: 0) + 1
        )

        localStorageManager?.saveDocument(
            document = doc,
            databaseId = databaseId,
            collectionId = id,
            syncStatus = if (isCreate) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE,
            baseVersion = existing?.version
        )

        RiviumSyncLogger.d("Set offline document: $documentId (${if (isCreate) "create" else "update"})")
        return doc
    }

    override suspend fun delete(documentId: String) {
        if (isOfflineEnabled) {
            deleteWithOfflineSupport(documentId)
            return
        }
        apiClient.deleteDocument(databaseId, id, documentId)
    }

    private suspend fun deleteWithOfflineSupport(documentId: String) {
        val isOnline = syncEngine?.isOnline?.value ?: true

        // Get current version for sync
        val existing = localStorageManager?.getDocument(documentId)
        val baseVersion = existing?.version ?: 1

        if (isOnline) {
            try {
                apiClient.deleteDocument(databaseId, id, documentId)
                // Remove from cache
                localStorageManager?.deleteDocument(documentId)
            } catch (e: Exception) {
                RiviumSyncLogger.w("Failed to delete document online, marking for delete: ${e.message}")
                // Mark for delete when back online
                localStorageManager?.markPendingDelete(documentId, databaseId, id, baseVersion)
            }
        } else {
            // Mark for delete when back online
            localStorageManager?.markPendingDelete(documentId, databaseId, id, baseVersion)
            RiviumSyncLogger.d("Marked offline document for deletion: $documentId")
        }
    }

    // ==================== Query Operations ====================

    override fun query(): SyncQuery {
        return SyncQueryImpl(databaseId, id, apiClient, mqttManager)
    }

    override fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery {
        return query().where(field, operator, value)
    }

    // ==================== Realtime Listeners ====================

    override fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration {
        val topic = mqttManager.collectionTopic(databaseId, id)
        RiviumSyncLogger.i("SyncCollection.listen: databaseId=$databaseId, collectionId=$id, topic=$topic")

        // For offline mode, also listen to local cache changes
        var localJob: Job? = null
        if (isOfflineEnabled) {
            localJob = scope.launch {
                localStorageManager?.getDocumentsFlow(databaseId, id)?.collect { docs ->
                    withContext(Dispatchers.Main) {
                        callback(docs)
                    }
                }
            }
        }

        // Initial fetch
        scope.launch {
            try {
                val documents = getAll()
                RiviumSyncLogger.i("SyncCollection.listen: initial fetch returned ${documents.size} docs")
                withContext(Dispatchers.Main) {
                    callback(documents)
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to fetch initial documents", e)
            }
        }

        // Subscribe to MQTT changes
        val handle = mqttManager.subscribe(topic) { payload ->
            RiviumSyncLogger.i("SyncCollection.listen: MQTT callback received for topic=$topic, payloadLength=${payload.length}")
            try {
                // Parse to validate JSON format
                val event = gson.fromJson<ChangeEvent>(payload, ChangeEvent::class.java)
                RiviumSyncLogger.i("SyncCollection.listen: parsed change event type=${event.type}, docId=${event.documentId}")
                // Refetch documents on any change
                scope.launch {
                    try {
                        val documents = getAll()
                        RiviumSyncLogger.i("SyncCollection.listen: refetch returned ${documents.size} docs")
                        withContext(Dispatchers.Main) {
                            callback(documents)
                        }
                    } catch (e: Exception) {
                        RiviumSyncLogger.e("Failed to refetch documents after change", e)
                    }
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to parse change event: payload=$payload", e)
            }
        }

        return ListenerRegistrationImpl {
            localJob?.cancel()
            mqttManager.unsubscribe(handle)
        }
    }

    override fun listenDocument(documentId: String, callback: (SyncDocument?) -> Unit): ListenerRegistration {
        // Subscribe to the collection topic and filter by documentId,
        // since the server publishes all changes to the collection-level topic
        val topic = mqttManager.collectionTopic(databaseId, id)
        RiviumSyncLogger.i("listenDocument: Subscribing to collection MQTT topic: $topic for documentId=$documentId")

        // For offline mode, also listen to local cache changes
        var localJob: Job? = null
        if (isOfflineEnabled) {
            localJob = scope.launch {
                localStorageManager?.getDocumentFlow(documentId)?.collect { doc ->
                    withContext(Dispatchers.Main) {
                        callback(doc)
                    }
                }
            }
        }

        // Initial fetch
        scope.launch {
            try {
                val document = get(documentId)
                withContext(Dispatchers.Main) {
                    callback(document)
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to fetch initial document", e)
            }
        }

        // Subscribe to MQTT changes on the collection topic, filtering by documentId
        val handle = mqttManager.subscribe(topic) { payload ->
            try {
                val jsonObject = gson.fromJson(payload, com.google.gson.JsonObject::class.java)
                val msgDocId = jsonObject.get("documentId")?.asString

                // Filter: only process messages for our specific document
                if (msgDocId != documentId) return@subscribe

                val type = jsonObject.get("type")?.asString
                val dataElement = jsonObject.get("data")
                val data: Map<String, Any?>? = if (dataElement != null && !dataElement.isJsonNull) {
                    gson.fromJson<Map<String, Any?>>(dataElement, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type)
                } else null
                val timestamp = jsonObject.get("timestamp")?.asString
                val version = jsonObject.get("version")?.asInt

                RiviumSyncLogger.i("listenDocument: Received change for documentId=$documentId, type=$type")
                when (type) {
                    "delete" -> {
                        // Also delete from local cache
                        if (isOfflineEnabled) {
                            scope.launch { localStorageManager?.deleteDocument(documentId) }
                        }
                        scope.launch(Dispatchers.Main) { callback(null) }
                    }
                    else -> {
                        // Use data directly from MQTT message if available
                        if (data != null) {
                            val now = System.currentTimeMillis()
                            // Parse timestamp from ISO string if available
                            val updatedAtFromTimestamp = timestamp?.let {
                                try {
                                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    }.parse(it)?.time ?: now
                                } catch (e: Exception) {
                                    now
                                }
                            } ?: now

                            val doc = SyncDocument(
                                id = msgDocId,
                                data = data,
                                createdAt = updatedAtFromTimestamp,
                                updatedAt = updatedAtFromTimestamp,
                                version = version ?: 1
                            )

                            // Update local cache
                            if (isOfflineEnabled) {
                                scope.launch {
                                    localStorageManager?.saveDocument(
                                        document = doc,
                                        databaseId = databaseId,
                                        collectionId = id,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                }
                            }

                            RiviumSyncLogger.i("listenDocument: Created document from MQTT data: id=${doc.id}, data=${doc.data}, version=${doc.version}")
                            scope.launch(Dispatchers.Main) { callback(doc) }
                        } else {
                            // Fallback to refetch if no data in message
                            RiviumSyncLogger.i("listenDocument: No data in MQTT message, refetching from API")
                            scope.launch {
                                try {
                                    val document = get(documentId)
                                    withContext(Dispatchers.Main) {
                                        callback(document)
                                    }
                                } catch (e: Exception) {
                                    RiviumSyncLogger.e("Failed to refetch document after change", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to parse change event", e)
            }
        }

        return ListenerRegistrationImpl {
            localJob?.cancel()
            mqttManager.unsubscribe(handle)
        }
    }

    // ==================== Callback-based Operations ====================

    override fun add(data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val doc = add(data)
                withContext(Dispatchers.Main) { onSuccess(doc) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun get(documentId: String, onSuccess: (SyncDocument?) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val doc = get(documentId)
                withContext(Dispatchers.Main) { onSuccess(doc) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun getAll(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val docs = getAll()
                withContext(Dispatchers.Main) { onSuccess(docs) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun update(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val doc = update(documentId, data)
                withContext(Dispatchers.Main) { onSuccess(doc) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun set(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val doc = set(documentId, data)
                withContext(Dispatchers.Main) { onSuccess(doc) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun delete(documentId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                delete(documentId)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    private data class ChangeEvent(
        val type: String, // "create", "update", "delete"
        val documentId: String?,
        val collectionName: String? = null,
        val data: Map<String, Any?>?,
        val timestamp: String? = null,
        val createdAt: Long? = null,
        val updatedAt: Long? = null,
        val version: Int? = null,
        val userId: String? = null
    )
}

/**
 * Implementation of ListenerRegistration
 */
private class ListenerRegistrationImpl(
    private val onRemove: () -> Unit
) : ListenerRegistration {
    override fun remove() {
        onRemove()
    }
}

/**
 * Implementation of SyncQuery
 */
internal class SyncQueryImpl(
    private val databaseId: String,
    private val collectionId: String,
    private val apiClient: ApiClient,
    private val mqttManager: MqttManager
) : SyncQuery {

    private val params = QueryParams()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    override fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery {
        params.filters.add(QueryFilter(field, operator.value, value))
        return this
    }

    override fun orderBy(field: String, direction: OrderDirection): SyncQuery {
        params.orderByField = field
        params.orderDirection = direction.value
        return this
    }

    override fun limit(count: Int): SyncQuery {
        params.limitCount = count
        return this
    }

    override fun offset(count: Int): SyncQuery {
        params.offsetCount = count
        return this
    }

    override suspend fun get(): List<SyncDocument> {
        return apiClient.queryDocuments(databaseId, collectionId, params)
    }

    override fun get(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val docs = get()
                withContext(Dispatchers.Main) { onSuccess(docs) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration {
        val topic = mqttManager.collectionTopic(databaseId, collectionId)

        // Initial fetch with query
        scope.launch {
            try {
                val documents = get()
                withContext(Dispatchers.Main) {
                    callback(documents)
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to fetch initial query results", e)
            }
        }

        // Subscribe to MQTT changes and re-run query
        val handle = mqttManager.subscribe(topic) { _ ->
            scope.launch {
                try {
                    val documents = get()
                    withContext(Dispatchers.Main) {
                        callback(documents)
                    }
                } catch (e: Exception) {
                    RiviumSyncLogger.e("Failed to refetch query results after change", e)
                }
            }
        }

        return ListenerRegistrationImpl { mqttManager.unsubscribe(handle) }
    }
}
