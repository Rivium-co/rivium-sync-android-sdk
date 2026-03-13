package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for API data models
 *
 * NOTE: ApiClient is internal, so full HTTP tests require instrumented tests.
 * These tests verify the data models used by ApiClient.
 */
class ApiClientTest {

    @Test
    fun `SyncDocument fromMap parses correctly`() {
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
    fun `SyncDocument toMap converts correctly`() {
        val doc = SyncDocument(
            id = "doc-456",
            data = mapOf("title" to "Test"),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 2
        )

        val map = doc.toMap()

        assertThat(map["id"]).isEqualTo("doc-456")
        assertThat((map["data"] as Map<*, *>)["title"]).isEqualTo("Test")
        assertThat(map["version"]).isEqualTo(2)
    }

    @Test
    fun `SyncDocument get methods work correctly`() {
        val doc = SyncDocument(
            id = "doc-1",
            data = mapOf(
                "name" to "John",
                "age" to 30.0, // JSON numbers come as Double
                "active" to true,
                "tags" to listOf("a", "b"),
                "meta" to mapOf("key" to "value")
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            version = 1
        )

        assertThat(doc.getString("name")).isEqualTo("John")
        assertThat(doc.getInt("age")).isEqualTo(30)
        assertThat(doc.getBoolean("active")).isTrue()
        assertThat(doc.getList<String>("tags")).containsExactly("a", "b")
        assertThat(doc.getMap("meta")?.get("key")).isEqualTo("value")
        assertThat(doc.contains("name")).isTrue()
        assertThat(doc.contains("nonexistent")).isFalse()
    }

    @Test
    fun `SyncDocument exists returns true for valid id`() {
        val doc = SyncDocument("doc-1", mapOf(), System.currentTimeMillis(), System.currentTimeMillis(), 1)
        assertThat(doc.exists()).isTrue()
    }

    @Test
    fun `SyncDocument exists returns false for empty id`() {
        val doc = SyncDocument("", mapOf(), System.currentTimeMillis(), System.currentTimeMillis(), 1)
        assertThat(doc.exists()).isFalse()
    }

    @Test
    fun `DatabaseInfo holds correct values`() {
        val info = DatabaseInfo(
            id = "db-123",
            name = "My Database",
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L
        )

        assertThat(info.id).isEqualTo("db-123")
        assertThat(info.name).isEqualTo("My Database")
        assertThat(info.createdAt).isEqualTo(1704067200000L)
    }

    @Test
    fun `CollectionInfo holds correct values`() {
        val info = CollectionInfo(
            id = "col-456",
            name = "users",
            databaseId = "db-123",
            documentCount = 42,
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L
        )

        assertThat(info.id).isEqualTo("col-456")
        assertThat(info.name).isEqualTo("users")
        assertThat(info.databaseId).isEqualTo("db-123")
        assertThat(info.documentCount).isEqualTo(42)
    }

    @Test
    fun `RiviumSyncConfig builder creates valid config`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .offlineEnabled(true)
            .offlineCacheSizeMb(50)
            .build()

        assertThat(config.apiKey).isEqualTo("nl_test_abc123")
        assertThat(config.debugMode).isTrue()
        assertThat(config.offlineEnabled).isTrue()
        assertThat(config.offlineCacheSizeMb).isEqualTo(50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RiviumSyncConfig builder rejects invalid api key`() {
        RiviumSyncConfig.Builder("invalid-key").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RiviumSyncConfig builder rejects empty api key`() {
        RiviumSyncConfig.Builder("").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RiviumSyncConfig builder rejects negative cache size`() {
        RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(-1)
            .build()
    }
}
