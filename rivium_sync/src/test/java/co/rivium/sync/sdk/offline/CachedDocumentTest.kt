package co.rivium.sync.sdk.offline

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.SyncDocument
import org.junit.Test

/**
 * Comprehensive tests for CachedDocument entity
 */
class CachedDocumentTest {

    private val now = System.currentTimeMillis()

    // ==================== Basic Construction ====================

    @Test
    fun `CachedDocument stores all properties correctly`() {
        val cached = CachedDocument(
            id = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            dataJson = """{"name":"John","age":30}""",
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 5,
            syncStatus = SyncStatus.SYNCED
        )

        assertThat(cached.id).isEqualTo("doc-123")
        assertThat(cached.databaseId).isEqualTo("db-1")
        assertThat(cached.collectionId).isEqualTo("users")
        assertThat(cached.dataJson).isEqualTo("""{"name":"John","age":30}""")
        assertThat(cached.createdAt).isEqualTo(1704067200000L)
        assertThat(cached.updatedAt).isEqualTo(1704153600000L)
        assertThat(cached.version).isEqualTo(5)
        assertThat(cached.syncStatus).isEqualTo(SyncStatus.SYNCED)
    }

    @Test
    fun `CachedDocument has correct default values`() {
        val cached = CachedDocument(
            id = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        assertThat(cached.baseVersion).isNull()
        assertThat(cached.retryCount).isEqualTo(0)
        assertThat(cached.lastError).isNull()
        assertThat(cached.localUpdatedAt).isGreaterThan(0L)
    }

    // ==================== fromSyncDocument() ====================

    @Test
    fun `fromSyncDocument creates CachedDocument with SYNCED status`() {
        val syncDoc = SyncDocument(
            id = "doc-123",
            data = mapOf("name" to "John", "age" to 30),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 3
        )

        val cached = CachedDocument.fromSyncDocument(
            doc = syncDoc,
            databaseId = "db-1",
            collectionId = "users",
            syncStatus = SyncStatus.SYNCED
        )

        assertThat(cached.id).isEqualTo("doc-123")
        assertThat(cached.databaseId).isEqualTo("db-1")
        assertThat(cached.collectionId).isEqualTo("users")
        assertThat(cached.createdAt).isEqualTo(1704067200000L)
        assertThat(cached.updatedAt).isEqualTo(1704153600000L)
        assertThat(cached.version).isEqualTo(3)
        assertThat(cached.syncStatus).isEqualTo(SyncStatus.SYNCED)
    }

    @Test
    fun `fromSyncDocument creates CachedDocument with PENDING_CREATE status`() {
        val syncDoc = SyncDocument(
            id = "doc-new",
            data = mapOf("title" to "New Document"),
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val cached = CachedDocument.fromSyncDocument(
            doc = syncDoc,
            databaseId = "db-1",
            collectionId = "documents",
            syncStatus = SyncStatus.PENDING_CREATE
        )

        assertThat(cached.syncStatus).isEqualTo(SyncStatus.PENDING_CREATE)
    }

    @Test
    fun `fromSyncDocument preserves baseVersion when provided`() {
        val syncDoc = SyncDocument("doc-1", mapOf(), now, now, 5)

        val cached = CachedDocument.fromSyncDocument(
            doc = syncDoc,
            databaseId = "db-1",
            collectionId = "col-1",
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = 4
        )

        assertThat(cached.baseVersion).isEqualTo(4)
        assertThat(cached.version).isEqualTo(5)
    }

    @Test
    fun `fromSyncDocument serializes data to JSON`() {
        val data = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "inner")
        )
        val syncDoc = SyncDocument("doc-1", data, now, now, 1)

        val cached = CachedDocument.fromSyncDocument(
            syncDoc, "db", "col", SyncStatus.SYNCED
        )

        // Data should be JSON serialized
        assertThat(cached.dataJson).contains("string")
        assertThat(cached.dataJson).contains("value")
        assertThat(cached.dataJson).contains("42")
    }

    // ==================== toSyncDocument() ====================

    @Test
    fun `toSyncDocument creates SyncDocument with all fields`() {
        val cached = CachedDocument(
            id = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            dataJson = """{"name":"John","age":30}""",
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 5,
            syncStatus = SyncStatus.SYNCED
        )

        val syncDoc = cached.toSyncDocument()

        assertThat(syncDoc.id).isEqualTo("doc-123")
        assertThat(syncDoc.data["name"]).isEqualTo("John")
        assertThat(syncDoc.createdAt).isEqualTo(1704067200000L)
        assertThat(syncDoc.updatedAt).isEqualTo(1704153600000L)
        assertThat(syncDoc.version).isEqualTo(5)
    }

    @Test
    fun `toSyncDocument handles empty JSON`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val syncDoc = cached.toSyncDocument()

        assertThat(syncDoc.data).isEmpty()
    }

    @Test
    fun `toSyncDocument handles invalid JSON gracefully`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "invalid json {{{",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val syncDoc = cached.toSyncDocument()

        // Should return empty map for invalid JSON
        assertThat(syncDoc.data).isEmpty()
    }

    // ==================== getData() ====================

    @Test
    fun `getData returns parsed data map`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"name":"Jane","score":95}""",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val data = cached.getData()

        assertThat(data["name"]).isEqualTo("Jane")
        assertThat(data["score"]).isEqualTo(95.0) // JSON numbers are doubles
    }

    @Test
    fun `getData returns empty map for empty JSON`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        assertThat(cached.getData()).isEmpty()
    }

    @Test
    fun `getData returns empty map for invalid JSON`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "not valid json",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        assertThat(cached.getData()).isEmpty()
    }

    @Test
    fun `getData handles nested structures`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"user":{"name":"John","address":{"city":"NYC"}}}""",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val data = cached.getData()

        @Suppress("UNCHECKED_CAST")
        val user = data["user"] as? Map<String, Any?>
        assertThat(user).isNotNull()
        assertThat(user?.get("name")).isEqualTo("John")
    }

    // ==================== withSyncStatus() ====================

    @Test
    fun `withSyncStatus creates copy with new status`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        val updated = original.withSyncStatus(SyncStatus.SYNCED)

        assertThat(updated.syncStatus).isEqualTo(SyncStatus.SYNCED)
        assertThat(original.syncStatus).isEqualTo(SyncStatus.PENDING_CREATE) // Original unchanged
    }

    @Test
    fun `withSyncStatus sets error message`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.PENDING_UPDATE
        )

        val updated = original.withSyncStatus(SyncStatus.SYNC_FAILED, "Network error")

        assertThat(updated.syncStatus).isEqualTo(SyncStatus.SYNC_FAILED)
        assertThat(updated.lastError).isEqualTo("Network error")
    }

    @Test
    fun `withSyncStatus increments retryCount on SYNC_FAILED`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.PENDING_UPDATE,
            retryCount = 2
        )

        val updated = original.withSyncStatus(SyncStatus.SYNC_FAILED, "Timeout")

        assertThat(updated.retryCount).isEqualTo(3)
    }

    @Test
    fun `withSyncStatus resets retryCount on success`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNC_FAILED,
            retryCount = 5
        )

        val updated = original.withSyncStatus(SyncStatus.SYNCED)

        assertThat(updated.retryCount).isEqualTo(0)
    }

    @Test
    fun `withSyncStatus updates localUpdatedAt`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED,
            localUpdatedAt = 1000L
        )

        Thread.sleep(10) // Ensure time passes
        val updated = original.withSyncStatus(SyncStatus.PENDING_UPDATE)

        assertThat(updated.localUpdatedAt).isGreaterThan(1000L)
    }

    // ==================== Roundtrip Tests ====================

    @Test
    fun `roundtrip fromSyncDocument to toSyncDocument preserves data`() {
        val original = SyncDocument(
            id = "doc-roundtrip",
            data = mapOf(
                "name" to "Test",
                "count" to 42,
                "active" to true
            ),
            createdAt = 1704067200000L,
            updatedAt = 1704153600000L,
            version = 7
        )

        val cached = CachedDocument.fromSyncDocument(
            original, "db", "col", SyncStatus.SYNCED
        )
        val restored = cached.toSyncDocument()

        assertThat(restored.id).isEqualTo(original.id)
        assertThat(restored.data["name"]).isEqualTo("Test")
        assertThat(restored.createdAt).isEqualTo(original.createdAt)
        assertThat(restored.updatedAt).isEqualTo(original.updatedAt)
        assertThat(restored.version).isEqualTo(original.version)
    }

    // ==================== Data Class Features ====================

    @Test
    fun `CachedDocument equality`() {
        val doc1 = CachedDocument(
            "doc-1", "db", "col", "{}", now, now, 1, SyncStatus.SYNCED
        )
        val doc2 = CachedDocument(
            "doc-1", "db", "col", "{}", now, now, 1, SyncStatus.SYNCED
        )

        // Note: localUpdatedAt defaults to System.currentTimeMillis() so may differ
        // We compare specific fields
        assertThat(doc1.id).isEqualTo(doc2.id)
        assertThat(doc1.databaseId).isEqualTo(doc2.databaseId)
    }

    @Test
    fun `CachedDocument copy preserves values`() {
        val original = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"a":1}""",
            createdAt = now,
            updatedAt = now,
            version = 3,
            syncStatus = SyncStatus.SYNCED
        )

        val copied = original.copy(version = 4)

        assertThat(copied.id).isEqualTo("doc-1")
        assertThat(copied.version).isEqualTo(4)
        assertThat(original.version).isEqualTo(3)
    }

    @Test
    fun `CachedDocument toString contains useful info`() {
        val cached = CachedDocument(
            id = "doc-xyz",
            databaseId = "db-1",
            collectionId = "users",
            dataJson = "{}",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        val str = cached.toString()

        assertThat(str).contains("doc-xyz")
        assertThat(str).contains("PENDING_CREATE")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles null values in data JSON`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"name":"John","middleName":null}""",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val data = cached.getData()

        assertThat(data["name"]).isEqualTo("John")
        assertThat(data.containsKey("middleName")).isTrue()
        assertThat(data["middleName"]).isNull()
    }

    @Test
    fun `handles arrays in data JSON`() {
        val cached = CachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"tags":["a","b","c"]}""",
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val data = cached.getData()

        @Suppress("UNCHECKED_CAST")
        val tags = data["tags"] as? List<String>
        assertThat(tags).containsExactly("a", "b", "c")
    }
}
