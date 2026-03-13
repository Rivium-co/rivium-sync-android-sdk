package co.rivium.sync.sdk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a document in a RiviumSync collection
 */
data class SyncDocument(
    val id: String,
    val data: Map<String, Any?>,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): SyncDocument {
            return gson.fromJson(json, SyncDocument::class.java)
        }

        fun fromMap(map: Map<String, Any?>): SyncDocument {
            return SyncDocument(
                id = map["id"] as? String ?: "",
                data = (map["data"] as? Map<String, Any?>) ?: emptyMap(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                version = (map["version"] as? Number)?.toInt() ?: 1
            )
        }
    }

    fun toJson(): String = gson.toJson(this)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "data" to data,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "version" to version
    )

    /**
     * Get a field value from the document data
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(field: String): T? = data[field] as? T

    /**
     * Get a string field
     */
    fun getString(field: String): String? = data[field] as? String

    /**
     * Get an int field
     */
    fun getInt(field: String): Int? = (data[field] as? Number)?.toInt()

    /**
     * Get a long field
     */
    fun getLong(field: String): Long? = (data[field] as? Number)?.toLong()

    /**
     * Get a double field
     */
    fun getDouble(field: String): Double? = (data[field] as? Number)?.toDouble()

    /**
     * Get a boolean field
     */
    fun getBoolean(field: String): Boolean? = data[field] as? Boolean

    /**
     * Get a list field
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getList(field: String): List<T>? = data[field] as? List<T>

    /**
     * Get a map field
     */
    @Suppress("UNCHECKED_CAST")
    fun getMap(field: String): Map<String, Any?>? = data[field] as? Map<String, Any?>

    /**
     * Check if document contains a field
     */
    fun contains(field: String): Boolean = data.containsKey(field)

    /**
     * Check if the document exists (has valid ID)
     */
    fun exists(): Boolean = id.isNotEmpty()
}

/**
 * Database info from server
 */
data class DatabaseInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Collection info from server
 */
data class CollectionInfo(
    val id: String,
    val name: String,
    val databaseId: String,
    val documentCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)
