package co.rivium.sync.sdk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP API client for RiviumSync REST operations
 */
internal class ApiClient(
    private val config: RiviumSyncConfig,
    private val userId: String? = null,
    /** Signed identity, when the app supplies one. */
    internal val userTokens: UserTokenStore = UserTokenStore(),
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.connectionTimeout.toLong(), TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        body: Any? = null
    ): Request {
        val url = "${config.apiUrl}$endpoint"
        val requestBody = body?.let {
            gson.toJson(it).toRequestBody(jsonMediaType)
        }

        val builder = Request.Builder()
            .url(url)
            .header("X-API-Key", config.apiKey)
            .header("Content-Type", "application/json")

        // A signed token decides who the caller is; the plain userId header is
        // only a fallback, and a project with requireSignedTokens refuses it.
        val token = userTokens.current()
        if (token != null) {
            builder.header("X-User-Token", token)
        } else {
            userId?.let { builder.header("X-User-Id", it) }
        }
        return builder.method(method, requestBody).build()
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        // Never log headers or bodies: they carry the API key, user tokens, the
        // realtime token and the app's own data.
        RiviumSyncLogger.d("API Request: ${request.method} ${request.url}")

        var response = client.newCall(request).execute()
        var responseBody = response.body?.string() ?: ""

        // An expired token is transient: get a fresh one and try once more, so
        // the caller never sees it.
        if (response.code == 401 && responseBody.contains("token_expired") && request.header("X-User-Token") != null) {
            RiviumSyncLogger.d("User token expired - refreshing and retrying once")
            userTokens.invalidate()
            val refreshed = userTokens.current()
            if (refreshed != null) {
                response = client.newCall(
                    request.newBuilder().header("X-User-Token", refreshed).build()
                ).execute()
                responseBody = response.body?.string() ?: ""
            }
        }

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errorMap = gson.fromJson<Map<String, Any?>>(responseBody, object : TypeToken<Map<String, Any?>>() {}.type)
                errorMap["message"]?.toString() ?: errorMap["error"]?.toString() ?: "Request failed"
            } catch (e: Exception) {
                "Request failed with status ${response.code}"
            }
            // The server's short error message only, never the body.
            RiviumSyncLogger.d("API Response (${response.code}): ${errorMsg.take(200)}")
            throw RiviumSyncException.NetworkException(errorMsg)
        }

        RiviumSyncLogger.d("API Response (${response.code}): ${responseBody.length} bytes")

        return gson.fromJson(responseBody, object : TypeToken<T>() {}.type)
    }

    // ==================== MQTT Token ====================

    /**
     * Fetch a short-lived JWT token for MQTT authentication.
     * The SDK uses this token as the MQTT password instead of exposing the API key.
     */
    fun fetchMqttToken(): MqttTokenResponse {
        val request = buildRequest("/connections/token", "POST", emptyMap<String, Any>())
        return executeRequest(request)
    }

    // ==================== Database Operations ====================

    suspend fun listDatabases(): List<DatabaseInfo> = withContext(Dispatchers.IO) {
        val request = buildRequest("/databases")
        val response: ApiResponse<List<Map<String, Any?>>> = executeRequest(request)
        response.data?.map { parseDatabaseInfo(it) } ?: emptyList()
    }


    // ==================== Collection Operations ====================

    suspend fun listCollections(databaseId: String): List<CollectionInfo> = withContext(Dispatchers.IO) {
        val request = buildRequest("/databases/$databaseId/collections")
        val response: ApiResponse<List<Map<String, Any?>>> = executeRequest(request)
        response.data?.map { parseCollectionInfo(it) } ?: emptyList()
    }

    suspend fun createCollection(databaseId: String, name: String): CollectionInfo = withContext(Dispatchers.IO) {
        val request = buildRequest("/databases/$databaseId/collections", "POST", mapOf("name" to name))
        val response: ApiResponse<Map<String, Any?>> = executeRequest(request)
        parseCollectionInfo(response.data ?: throw RiviumSyncException.CollectionException("Failed to create collection"))
    }

    suspend fun deleteCollection(collectionId: String) = withContext(Dispatchers.IO) {
        val request = buildRequest("/collections/$collectionId", "DELETE")
        executeRequest<ApiResponse<Any?>>(request)
    }

    // ==================== Document Operations ====================

    suspend fun addDocument(
        databaseId: String,
        collectionId: String,
        data: Map<String, Any?>
    ): SyncDocument = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/databases/$databaseId/collections/$collectionId/documents/sdk",
            "POST",
            mapOf("data" to data)
        )
        val response: ApiResponse<Map<String, Any?>> = executeRequest(request)
        SyncDocument.fromMap(response.data ?: throw RiviumSyncException.DocumentException("Failed to add document"))
    }

    suspend fun getDocument(
        databaseId: String,
        collectionId: String,
        documentId: String
    ): SyncDocument? = withContext(Dispatchers.IO) {
        val request = buildRequest("/databases/$databaseId/collections/$collectionId/documents/sdk/$documentId")
        try {
            val response: ApiResponse<Map<String, Any?>> = executeRequest(request)
            response.data?.let { SyncDocument.fromMap(it) }
        } catch (e: RiviumSyncException.NetworkException) {
            if (e.message?.contains("404") == true || e.message?.contains("not found") == true) {
                null
            } else {
                throw e
            }
        }
    }

    suspend fun getAllDocuments(
        databaseId: String,
        collectionId: String
    ): List<SyncDocument> = fetchCollection(databaseId, collectionId).documents

    /**
     * Every document in a collection, across as many pages as it takes. The
     * server answers 100 at a time unless asked otherwise, and this used to
     * make one request - so getAll() quietly stopped at the first 100.
     */
    suspend fun fetchCollection(
        databaseId: String,
        collectionId: String
    ): CollectionSnapshot = withContext(Dispatchers.IO) {
        val documents = mutableListOf<SyncDocument>()
        var total: Int? = null
        while (true) {
            val request = buildRequest(
                "/databases/$databaseId/collections/$collectionId/documents/sdk" +
                    "?skip=${documents.size}&limit=$PAGE_SIZE"
            )
            val response: ApiResponse<List<Map<String, Any?>>> = executeRequest(request)
            val page = response.data?.map { SyncDocument.fromMap(it) } ?: emptyList()
            documents += page
            total = response.total
            // Stop on a short or empty page, or once we have everything.
            if (page.size < PAGE_SIZE || total == null || documents.size >= total) break
        }
        RiviumSyncLogger.d("getAllDocuments fetched ${documents.size} of ${total ?: "?"}")
        CollectionSnapshot(documents, complete = total != null && documents.size >= total)
    }

    suspend fun updateDocument(
        databaseId: String,
        collectionId: String,
        documentId: String,
        data: Map<String, Any?>
    ): SyncDocument = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/databases/$databaseId/collections/$collectionId/documents/sdk/$documentId",
            "PATCH",
            mapOf("data" to data)
        )
        val response: ApiResponse<Map<String, Any?>> = executeRequest(request)
        SyncDocument.fromMap(response.data ?: throw RiviumSyncException.DocumentException("Failed to update document"))
    }

    suspend fun setDocument(
        databaseId: String,
        collectionId: String,
        documentId: String,
        data: Map<String, Any?>
    ): SyncDocument = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/databases/$databaseId/collections/$collectionId/documents/sdk/$documentId",
            "PUT",
            mapOf("data" to data)
        )
        val response: ApiResponse<Map<String, Any?>> = executeRequest(request)
        SyncDocument.fromMap(response.data ?: throw RiviumSyncException.DocumentException("Failed to set document"))
    }

    suspend fun deleteDocument(
        databaseId: String,
        collectionId: String,
        documentId: String
    ) = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/databases/$databaseId/collections/$collectionId/documents/sdk/$documentId",
            "DELETE"
        )
        executeRequest<ApiResponse<Any?>>(request)
    }

    suspend fun queryDocuments(
        databaseId: String,
        collectionId: String,
        query: QueryParams
    ): List<SyncDocument> = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/databases/$databaseId/collections/$collectionId/documents/sdk/query",
            "POST",
            query.toMap()
        )
        val response: ApiResponse<List<Map<String, Any?>>> = executeRequest(request)
        response.data?.map { SyncDocument.fromMap(it) } ?: emptyList()
    }

    // ==================== Batch Operations ====================

    /**
     * Execute a batch of operations atomically
     */
    suspend fun executeBatch(operations: List<Map<String, Any?>>) = withContext(Dispatchers.IO) {
        val request = buildRequest(
            "/batch/sdk",
            "POST",
            mapOf("operations" to operations)
        )
        executeRequest<ApiResponse<Any?>>(request)
    }

    // ==================== Helpers ====================

    private fun parseDatabaseInfo(map: Map<String, Any?>): DatabaseInfo {
        return DatabaseInfo(
            id = map["id"] as? String ?: "",
            name = map["name"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0
        )
    }

    private fun parseCollectionInfo(map: Map<String, Any?>): CollectionInfo {
        return CollectionInfo(
            id = map["id"] as? String ?: "",
            name = map["name"] as? String ?: "",
            databaseId = map["databaseId"] as? String ?: "",
            documentCount = (map["documentCount"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0
        )
    }

    companion object {
        internal const val PAGE_SIZE = 100
    }
}

/**
 * MQTT token response from /connections/token
 */
internal data class MqttTokenResponse(
    val token: String,
    val expiresIn: String?,
    /** Topics are namespaced by project; the SDK needs this to build them. */
    val projectId: String?,
    val mqtt: MqttConnectionInfo?
)

internal data class MqttConnectionInfo(
    val host: String?,
    val port: Int?,
    val useTls: Boolean?
)

/**
 * API response wrapper
 * Note: All fields are nullable since backend may not include all fields
 */
internal data class ApiResponse<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
    /** Size of the whole result set on paged endpoints. */
    val total: Int? = null
)

/**
 * A whole collection as read from the server. [complete] is true only when the
 * pages added up to the server's own total, which is what makes it safe to drop
 * cached rows that were not returned.
 */
internal data class CollectionSnapshot(
    val documents: List<SyncDocument>,
    val complete: Boolean
)

/**
 * Query parameters for document queries
 */
internal data class QueryParams(
    val filters: MutableList<QueryFilter> = mutableListOf(),
    var orderByField: String? = null,
    var orderDirection: String = "asc",
    var limitCount: Int? = null,
    var offsetCount: Int? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        if (filters.isNotEmpty()) {
            put("filters", filters.map { it.toMap() })
        }
        orderByField?.let {
            put("orderBy", mapOf("field" to it, "direction" to orderDirection))
        }
        limitCount?.let { put("limit", it) }
        offsetCount?.let { put("offset", it) }
    }
}

internal data class QueryFilter(
    val field: String,
    val operator: String,
    val value: Any?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "field" to field,
        "operator" to operator,
        "value" to value
    )
}
