package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Comprehensive tests for SyncDocument data class
 * Tests all public methods: get, getString, getInt, getLong, getDouble,
 * getBoolean, getList, getMap, contains, exists, toJson, toMap, fromJson, fromMap
 */
class SyncDocumentTest {

    private val now = System.currentTimeMillis()

    // ==================== Basic Properties ====================

    @Test
    fun `SyncDocument stores all properties correctly`() {
        val data = mapOf("name" to "John", "age" to 30)
        val doc = SyncDocument(
            id = "doc-123",
            data = data,
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 5
        )

        assertThat(doc.id).isEqualTo("doc-123")
        assertThat(doc.data).isEqualTo(data)
        assertThat(doc.createdAt).isEqualTo(1704067200000L)
        assertThat(doc.updatedAt).isEqualTo(1704153600000L)
        assertThat(doc.version).isEqualTo(5)
    }

    @Test
    fun `SyncDocument with empty data`() {
        val doc = SyncDocument("doc-1", emptyMap(), now, now, 1)

        assertThat(doc.data).isEmpty()
        assertThat(doc.exists()).isTrue()
    }

    // ==================== exists() ====================

    @Test
    fun `exists returns true for valid id`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.exists()).isTrue()
    }

    @Test
    fun `exists returns false for empty id`() {
        val doc = SyncDocument("", mapOf(), now, now, 1)
        assertThat(doc.exists()).isFalse()
    }

    @Test
    fun `exists returns true for whitespace id`() {
        val doc = SyncDocument("   ", mapOf(), now, now, 1)
        assertThat(doc.exists()).isTrue() // whitespace is still a non-empty string
    }

    // ==================== contains() ====================

    @Test
    fun `contains returns true for existing field`() {
        val doc = SyncDocument("doc-1", mapOf("name" to "John"), now, now, 1)
        assertThat(doc.contains("name")).isTrue()
    }

    @Test
    fun `contains returns false for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf("name" to "John"), now, now, 1)
        assertThat(doc.contains("age")).isFalse()
    }

    @Test
    fun `contains returns true for null value field`() {
        val doc = SyncDocument("doc-1", mapOf("name" to null), now, now, 1)
        assertThat(doc.contains("name")).isTrue()
    }

    // ==================== get<T>() generic method ====================

    @Test
    fun `get returns value with correct type`() {
        val doc = SyncDocument("doc-1", mapOf("name" to "John", "count" to 42), now, now, 1)

        val name: String? = doc.get("name")
        val count: Int? = doc.get("count")

        assertThat(name).isEqualTo("John")
        assertThat(count).isEqualTo(42)
    }

    @Test
    fun `get returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf("name" to "John"), now, now, 1)

        val result: String? = doc.get("nonexistent")

        assertThat(result).isNull()
    }

    // ==================== getString() ====================

    @Test
    fun `getString returns string value`() {
        val doc = SyncDocument("doc-1", mapOf("name" to "John Doe"), now, now, 1)
        assertThat(doc.getString("name")).isEqualTo("John Doe")
    }

    @Test
    fun `getString returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getString("name")).isNull()
    }

    @Test
    fun `getString returns null for non-string value`() {
        val doc = SyncDocument("doc-1", mapOf("age" to 30), now, now, 1)
        assertThat(doc.getString("age")).isNull()
    }

    @Test
    fun `getString handles empty string`() {
        val doc = SyncDocument("doc-1", mapOf("name" to ""), now, now, 1)
        assertThat(doc.getString("name")).isEqualTo("")
    }

    // ==================== getInt() ====================

    @Test
    fun `getInt returns integer value`() {
        val doc = SyncDocument("doc-1", mapOf("age" to 30), now, now, 1)
        assertThat(doc.getInt("age")).isEqualTo(30)
    }

    @Test
    fun `getInt converts double to int`() {
        val doc = SyncDocument("doc-1", mapOf("age" to 30.0), now, now, 1)
        assertThat(doc.getInt("age")).isEqualTo(30)
    }

    @Test
    fun `getInt converts long to int`() {
        val doc = SyncDocument("doc-1", mapOf("age" to 30L), now, now, 1)
        assertThat(doc.getInt("age")).isEqualTo(30)
    }

    @Test
    fun `getInt returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getInt("age")).isNull()
    }

    @Test
    fun `getInt returns null for string value`() {
        val doc = SyncDocument("doc-1", mapOf("age" to "thirty"), now, now, 1)
        assertThat(doc.getInt("age")).isNull()
    }

    @Test
    fun `getInt handles zero`() {
        val doc = SyncDocument("doc-1", mapOf("count" to 0), now, now, 1)
        assertThat(doc.getInt("count")).isEqualTo(0)
    }

    @Test
    fun `getInt handles negative numbers`() {
        val doc = SyncDocument("doc-1", mapOf("balance" to -100), now, now, 1)
        assertThat(doc.getInt("balance")).isEqualTo(-100)
    }

    // ==================== getLong() ====================

    @Test
    fun `getLong returns long value`() {
        val doc = SyncDocument("doc-1", mapOf("timestamp" to 1704067200000L), now, now, 1)
        assertThat(doc.getLong("timestamp")).isEqualTo(1704067200000L)
    }

    @Test
    fun `getLong converts int to long`() {
        val doc = SyncDocument("doc-1", mapOf("count" to 42), now, now, 1)
        assertThat(doc.getLong("count")).isEqualTo(42L)
    }

    @Test
    fun `getLong converts double to long`() {
        val doc = SyncDocument("doc-1", mapOf("value" to 42.9), now, now, 1)
        assertThat(doc.getLong("value")).isEqualTo(42L)
    }

    @Test
    fun `getLong returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getLong("timestamp")).isNull()
    }

    // ==================== getDouble() ====================

    @Test
    fun `getDouble returns double value`() {
        val doc = SyncDocument("doc-1", mapOf("price" to 19.99), now, now, 1)
        assertThat(doc.getDouble("price")).isEqualTo(19.99)
    }

    @Test
    fun `getDouble converts int to double`() {
        val doc = SyncDocument("doc-1", mapOf("price" to 20), now, now, 1)
        assertThat(doc.getDouble("price")).isEqualTo(20.0)
    }

    @Test
    fun `getDouble converts long to double`() {
        val doc = SyncDocument("doc-1", mapOf("value" to 100L), now, now, 1)
        assertThat(doc.getDouble("value")).isEqualTo(100.0)
    }

    @Test
    fun `getDouble returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getDouble("price")).isNull()
    }

    @Test
    fun `getDouble handles negative values`() {
        val doc = SyncDocument("doc-1", mapOf("balance" to -99.99), now, now, 1)
        assertThat(doc.getDouble("balance")).isEqualTo(-99.99)
    }

    // ==================== getBoolean() ====================

    @Test
    fun `getBoolean returns true`() {
        val doc = SyncDocument("doc-1", mapOf("active" to true), now, now, 1)
        assertThat(doc.getBoolean("active")).isTrue()
    }

    @Test
    fun `getBoolean returns false`() {
        val doc = SyncDocument("doc-1", mapOf("active" to false), now, now, 1)
        assertThat(doc.getBoolean("active")).isFalse()
    }

    @Test
    fun `getBoolean returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getBoolean("active")).isNull()
    }

    @Test
    fun `getBoolean returns null for non-boolean value`() {
        val doc = SyncDocument("doc-1", mapOf("active" to "yes"), now, now, 1)
        assertThat(doc.getBoolean("active")).isNull()
    }

    @Test
    fun `getBoolean returns null for numeric value`() {
        val doc = SyncDocument("doc-1", mapOf("active" to 1), now, now, 1)
        assertThat(doc.getBoolean("active")).isNull()
    }

    // ==================== getList<T>() ====================

    @Test
    fun `getList returns string list`() {
        val doc = SyncDocument("doc-1", mapOf("tags" to listOf("kotlin", "android", "sdk")), now, now, 1)

        val tags = doc.getList<String>("tags")

        assertThat(tags).containsExactly("kotlin", "android", "sdk")
    }

    @Test
    fun `getList returns integer list`() {
        val doc = SyncDocument("doc-1", mapOf("scores" to listOf(100, 90, 85)), now, now, 1)

        val scores = doc.getList<Int>("scores")

        assertThat(scores).containsExactly(100, 90, 85)
    }

    @Test
    fun `getList returns empty list`() {
        val doc = SyncDocument("doc-1", mapOf("items" to emptyList<String>()), now, now, 1)

        val items = doc.getList<String>("items")

        assertThat(items).isEmpty()
    }

    @Test
    fun `getList returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getList<String>("tags")).isNull()
    }

    @Test
    fun `getList returns null for non-list value`() {
        val doc = SyncDocument("doc-1", mapOf("tags" to "single"), now, now, 1)
        assertThat(doc.getList<String>("tags")).isNull()
    }

    // ==================== getMap() ====================

    @Test
    fun `getMap returns nested map`() {
        val metadata = mapOf("author" to "John", "version" to 1)
        val doc = SyncDocument("doc-1", mapOf("metadata" to metadata), now, now, 1)

        val result = doc.getMap("metadata")

        assertThat(result).isEqualTo(metadata)
        assertThat(result?.get("author")).isEqualTo("John")
        assertThat(result?.get("version")).isEqualTo(1)
    }

    @Test
    fun `getMap returns empty map`() {
        val doc = SyncDocument("doc-1", mapOf("settings" to emptyMap<String, Any>()), now, now, 1)

        val result = doc.getMap("settings")

        assertThat(result).isEmpty()
    }

    @Test
    fun `getMap returns null for non-existing field`() {
        val doc = SyncDocument("doc-1", mapOf(), now, now, 1)
        assertThat(doc.getMap("metadata")).isNull()
    }

    @Test
    fun `getMap returns null for non-map value`() {
        val doc = SyncDocument("doc-1", mapOf("metadata" to "string"), now, now, 1)
        assertThat(doc.getMap("metadata")).isNull()
    }

    @Test
    fun `getMap handles deeply nested maps`() {
        val nested = mapOf(
            "level1" to mapOf(
                "level2" to mapOf(
                    "value" to "deep"
                )
            )
        )
        val doc = SyncDocument("doc-1", mapOf("nested" to nested), now, now, 1)

        val result = doc.getMap("nested")

        assertThat(result).isNotNull()
        @Suppress("UNCHECKED_CAST")
        val level1 = result?.get("level1") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val level2 = level1?.get("level2") as? Map<String, Any?>
        assertThat(level2?.get("value")).isEqualTo("deep")
    }

    // ==================== toMap() ====================

    @Test
    fun `toMap includes all fields`() {
        val doc = SyncDocument(
            id = "doc-123",
            data = mapOf("name" to "John"),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 3
        )

        val map = doc.toMap()

        assertThat(map["id"]).isEqualTo("doc-123")
        assertThat(map["data"]).isEqualTo(mapOf("name" to "John"))
        assertThat(map["createdAt"]).isEqualTo(1704067200000L)
        assertThat(map["updatedAt"]).isEqualTo(1704153600000L)
        assertThat(map["version"]).isEqualTo(3)
    }

    @Test
    fun `toMap with empty data`() {
        val doc = SyncDocument("doc-1", emptyMap(), now, now, 1)

        val map = doc.toMap()

        assertThat(map["data"]).isEqualTo(emptyMap<String, Any?>())
    }

    // ==================== fromMap() ====================

    @Test
    fun `fromMap creates document with all fields`() {
        val map = mapOf(
            "id" to "doc-123",
            "data" to mapOf("name" to "John", "age" to 30),
            "createdAt" to 1704067200000L,
            "updatedAt" to 1704153600000L,
            "version" to 5
        )

        val doc = SyncDocument.fromMap(map)

        assertThat(doc.id).isEqualTo("doc-123")
        assertThat(doc.data["name"]).isEqualTo("John")
        assertThat(doc.data["age"]).isEqualTo(30)
        assertThat(doc.createdAt).isEqualTo(1704067200000L)
        assertThat(doc.updatedAt).isEqualTo(1704153600000L)
        assertThat(doc.version).isEqualTo(5)
    }

    @Test
    fun `fromMap handles missing optional fields with defaults`() {
        val map = mapOf(
            "id" to "doc-123",
            "data" to mapOf("name" to "John")
        )

        val doc = SyncDocument.fromMap(map)

        assertThat(doc.id).isEqualTo("doc-123")
        assertThat(doc.data["name"]).isEqualTo("John")
        // Default values for missing fields
        assertThat(doc.version).isEqualTo(1)
    }

    @Test
    fun `fromMap handles integer timestamps`() {
        val map = mapOf(
            "id" to "doc-123",
            "data" to mapOf("name" to "John"),
            "createdAt" to 1704067200000, // Int instead of Long
            "updatedAt" to 1704153600000,
            "version" to 1
        )

        val doc = SyncDocument.fromMap(map)

        assertThat(doc.createdAt).isEqualTo(1704067200000L)
    }

    // ==================== toJson() / fromJson() ====================

    @Test
    fun `toJson creates valid JSON string`() {
        val doc = SyncDocument(
            id = "doc-123",
            data = mapOf("name" to "John"),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 1
        )

        val json = doc.toJson()

        assertThat(json).contains("doc-123")
        assertThat(json).contains("John")
    }

    @Test
    fun `fromJson parses JSON string`() {
        val json = """{"id":"doc-123","data":{"name":"John"},"createdAt":1704067200000,"updatedAt":1704153600000,"version":2}"""

        val doc = SyncDocument.fromJson(json)

        assertThat(doc.id).isEqualTo("doc-123")
        assertThat(doc.data["name"]).isEqualTo("John")
        assertThat(doc.version).isEqualTo(2)
    }

    @Test
    fun `toJson and fromJson roundtrip preserves data`() {
        val original = SyncDocument(
            id = "doc-roundtrip",
            data = mapOf(
                "string" to "value",
                "number" to 42,
                "boolean" to true,
                "list" to listOf("a", "b"),
                "map" to mapOf("nested" to "data")
            ),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 7
        )

        val json = original.toJson()
        val restored = SyncDocument.fromJson(json)

        assertThat(restored.id).isEqualTo(original.id)
        assertThat(restored.data["string"]).isEqualTo("value")
        assertThat(restored.data["boolean"]).isEqualTo(true)
        assertThat(restored.version).isEqualTo(7)
    }

    // ==================== Data Class Features ====================

    @Test
    fun `equals compares all fields`() {
        val doc1 = SyncDocument("doc-1", mapOf("a" to 1), 1000L, 2000L, 1)
        val doc2 = SyncDocument("doc-1", mapOf("a" to 1), 1000L, 2000L, 1)
        val doc3 = SyncDocument("doc-1", mapOf("a" to 2), 1000L, 2000L, 1)

        assertThat(doc1).isEqualTo(doc2)
        assertThat(doc1).isNotEqualTo(doc3)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val doc1 = SyncDocument("doc-1", mapOf("a" to 1), 1000L, 2000L, 1)
        val doc2 = SyncDocument("doc-1", mapOf("a" to 1), 1000L, 2000L, 1)

        assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode())
    }

    @Test
    fun `copy creates modified document`() {
        val original = SyncDocument("doc-1", mapOf("a" to 1), 1000L, 2000L, 1)

        val copied = original.copy(version = 2)

        assertThat(copied.id).isEqualTo(original.id)
        assertThat(copied.version).isEqualTo(2)
        assertThat(original.version).isEqualTo(1) // Original unchanged
    }

    @Test
    fun `toString contains useful information`() {
        val doc = SyncDocument("doc-123", mapOf("name" to "Test"), now, now, 5)

        val str = doc.toString()

        assertThat(str).contains("doc-123")
        assertThat(str).contains("5")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles null values in data`() {
        val doc = SyncDocument("doc-1", mapOf("value" to null), now, now, 1)

        assertThat(doc.contains("value")).isTrue()
        assertThat(doc.get<String>("value")).isNull()
        assertThat(doc.getString("value")).isNull()
    }

    @Test
    fun `handles special characters in string values`() {
        val doc = SyncDocument(
            "doc-1",
            mapOf("text" to "Hello\nWorld\t\"quoted\""),
            now, now, 1
        )

        assertThat(doc.getString("text")).isEqualTo("Hello\nWorld\t\"quoted\"")
    }

    @Test
    fun `handles unicode in string values`() {
        val doc = SyncDocument(
            "doc-1",
            mapOf("emoji" to "Hello 👋 World 🌍"),
            now, now, 1
        )

        assertThat(doc.getString("emoji")).isEqualTo("Hello 👋 World 🌍")
    }

    @Test
    fun `handles large numbers`() {
        val doc = SyncDocument(
            "doc-1",
            mapOf("bigLong" to Long.MAX_VALUE, "bigDouble" to Double.MAX_VALUE),
            now, now, 1
        )

        assertThat(doc.getLong("bigLong")).isEqualTo(Long.MAX_VALUE)
        assertThat(doc.getDouble("bigDouble")).isEqualTo(Double.MAX_VALUE)
    }
}
