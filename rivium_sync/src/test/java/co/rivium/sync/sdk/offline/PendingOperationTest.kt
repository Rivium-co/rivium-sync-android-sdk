package co.rivium.sync.sdk.offline

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Comprehensive tests for PendingOperation entity
 *
 * Note: getData() requires Gson which is only available on Android runtime,
 * so we test the JSON serialization through dataJson field inspection.
 */
class PendingOperationTest {

    private val now = System.currentTimeMillis()

    // ==================== Factory Method: create() ====================

    @Test
    fun `create factory method creates CREATE operation`() {
        val data = mapOf("name" to "John", "age" to 30)

        val op = PendingOperation.create(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            data = data
        )

        assertThat(op.documentId).isEqualTo("doc-123")
        assertThat(op.databaseId).isEqualTo("db-1")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.operationType).isEqualTo(OperationType.CREATE)
        assertThat(op.dataJson).isNotNull()
        assertThat(op.baseVersion).isNull()
    }

    @Test
    fun `create factory serializes data to JSON`() {
        val data = mapOf("title" to "Test", "count" to 42)

        val op = PendingOperation.create(
            "doc-1", "db-1", "col-1", data
        )

        assertThat(op.dataJson).contains("title")
        assertThat(op.dataJson).contains("Test")
        assertThat(op.dataJson).contains("42")
    }

    @Test
    fun `create with empty data map produces empty JSON object`() {
        val op = PendingOperation.create(
            "doc-1", "db-1", "col-1", emptyMap()
        )

        assertThat(op.dataJson).isEqualTo("{}")
    }

    // ==================== Factory Method: update() ====================

    @Test
    fun `update factory method creates UPDATE operation`() {
        val data = mapOf("status" to "active")

        val op = PendingOperation.update(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            data = data,
            baseVersion = 5
        )

        assertThat(op.documentId).isEqualTo("doc-123")
        assertThat(op.databaseId).isEqualTo("db-1")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.operationType).isEqualTo(OperationType.UPDATE)
        assertThat(op.dataJson).isNotNull()
        assertThat(op.baseVersion).isEqualTo(5)
    }

    @Test
    fun `update serializes data to JSON`() {
        val op = PendingOperation.update(
            "doc-1", "db-1", "col-1",
            mapOf("status" to "completed"),
            baseVersion = 1
        )

        assertThat(op.dataJson).contains("status")
        assertThat(op.dataJson).contains("completed")
    }

    @Test
    fun `update preserves baseVersion for conflict detection`() {
        val op = PendingOperation.update(
            "doc-1", "db-1", "col-1",
            mapOf("x" to 1),
            baseVersion = 10
        )

        assertThat(op.baseVersion).isEqualTo(10)
    }

    // ==================== Factory Method: delete() ====================

    @Test
    fun `delete factory method creates DELETE operation`() {
        val op = PendingOperation.delete(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            baseVersion = 3
        )

        assertThat(op.documentId).isEqualTo("doc-123")
        assertThat(op.databaseId).isEqualTo("db-1")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.operationType).isEqualTo(OperationType.DELETE)
        assertThat(op.dataJson).isNull()
        assertThat(op.baseVersion).isEqualTo(3)
    }

    @Test
    fun `delete has null dataJson`() {
        val op = PendingOperation.delete("doc-1", "db-1", "col-1", 1)

        assertThat(op.dataJson).isNull()
    }

    // ==================== Default Values ====================

    @Test
    fun `default values are set correctly`() {
        val op = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        assertThat(op.operationId).isEqualTo(0) // Auto-generated
        assertThat(op.retryCount).isEqualTo(0)
        assertThat(op.lastError).isNull()
        assertThat(op.processing).isFalse()
        assertThat(op.createdAt).isGreaterThan(0L)
    }

    // ==================== withRetry() ====================

    @Test
    fun `withRetry increments retryCount`() {
        val original = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        val retried = original.withRetry("Network timeout")

        assertThat(retried.retryCount).isEqualTo(1)
        assertThat(original.retryCount).isEqualTo(0) // Original unchanged
    }

    @Test
    fun `withRetry sets lastError`() {
        val original = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        val retried = original.withRetry("Connection refused")

        assertThat(retried.lastError).isEqualTo("Connection refused")
    }

    @Test
    fun `withRetry clears processing flag`() {
        val original = PendingOperation(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            operationType = OperationType.CREATE,
            dataJson = "{}",
            baseVersion = null,
            processing = true
        )

        val retried = original.withRetry("Error")

        assertThat(retried.processing).isFalse()
    }

    @Test
    fun `withRetry accumulates retry count`() {
        var op = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        op = op.withRetry("Error 1")
        assertThat(op.retryCount).isEqualTo(1)

        op = op.withRetry("Error 2")
        assertThat(op.retryCount).isEqualTo(2)

        op = op.withRetry("Error 3")
        assertThat(op.retryCount).isEqualTo(3)
    }

    // ==================== markProcessing() ====================

    @Test
    fun `markProcessing sets processing flag`() {
        val original = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        val processing = original.markProcessing()

        assertThat(processing.processing).isTrue()
        assertThat(original.processing).isFalse() // Original unchanged
    }

    @Test
    fun `markProcessing preserves other fields`() {
        val original = PendingOperation.update(
            "doc-1", "db-1", "col-1",
            mapOf("a" to 1),
            baseVersion = 5
        )

        val processing = original.markProcessing()

        assertThat(processing.documentId).isEqualTo("doc-1")
        assertThat(processing.operationType).isEqualTo(OperationType.UPDATE)
        assertThat(processing.baseVersion).isEqualTo(5)
        assertThat(processing.dataJson).isEqualTo(original.dataJson)
    }

    // ==================== Data Class Features ====================

    @Test
    fun `PendingOperation equality`() {
        val op1 = PendingOperation(
            operationId = 1,
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            operationType = OperationType.CREATE,
            dataJson = """{"a":1}""",
            baseVersion = null,
            createdAt = 1000L
        )

        val op2 = PendingOperation(
            operationId = 1,
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            operationType = OperationType.CREATE,
            dataJson = """{"a":1}""",
            baseVersion = null,
            createdAt = 1000L
        )

        assertThat(op1).isEqualTo(op2)
    }

    @Test
    fun `PendingOperation copy`() {
        val original = PendingOperation.create("doc-1", "db-1", "col-1", mapOf("a" to 1))

        val copied = original.copy(documentId = "doc-2")

        assertThat(copied.documentId).isEqualTo("doc-2")
        assertThat(original.documentId).isEqualTo("doc-1")
    }

    @Test
    fun `PendingOperation toString contains useful info`() {
        val op = PendingOperation.create("doc-xyz", "db-1", "users", mapOf("name" to "Test"))

        val str = op.toString()

        assertThat(str).contains("doc-xyz")
        assertThat(str).contains("CREATE")
    }

    // ==================== Operation Type Specific Tests ====================

    @Test
    fun `CREATE operation has no baseVersion`() {
        val op = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        assertThat(op.operationType).isEqualTo(OperationType.CREATE)
        assertThat(op.baseVersion).isNull()
    }

    @Test
    fun `UPDATE operation requires baseVersion`() {
        val op = PendingOperation.update("doc-1", "db-1", "col-1", mapOf(), baseVersion = 3)

        assertThat(op.operationType).isEqualTo(OperationType.UPDATE)
        assertThat(op.baseVersion).isEqualTo(3)
    }

    @Test
    fun `DELETE operation requires baseVersion`() {
        val op = PendingOperation.delete("doc-1", "db-1", "col-1", baseVersion = 5)

        assertThat(op.operationType).isEqualTo(OperationType.DELETE)
        assertThat(op.baseVersion).isEqualTo(5)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles special characters in document ID`() {
        val op = PendingOperation.create(
            "doc/with/slashes:and:colons",
            "db-1",
            "col-1",
            mapOf()
        )

        assertThat(op.documentId).isEqualTo("doc/with/slashes:and:colons")
    }

    @Test
    fun `serializes nested data to JSON`() {
        val nestedData = mapOf(
            "user" to mapOf(
                "name" to "John",
                "address" to mapOf("city" to "NYC")
            )
        )

        val op = PendingOperation.create("doc-1", "db-1", "col-1", nestedData)

        assertThat(op.dataJson).contains("user")
        assertThat(op.dataJson).contains("John")
        assertThat(op.dataJson).contains("NYC")
    }

    @Test
    fun `serializes arrays to JSON`() {
        val op = PendingOperation.create(
            "doc-1", "db-1", "col-1",
            mapOf("tags" to listOf("kotlin", "android", "sdk"))
        )

        assertThat(op.dataJson).contains("tags")
        assertThat(op.dataJson).contains("kotlin")
        assertThat(op.dataJson).contains("android")
        assertThat(op.dataJson).contains("sdk")
    }

    @Test
    fun `serializes large data maps to JSON`() {
        val largeData = (1..100).associate { "field$it" to "value$it" }

        val op = PendingOperation.create("doc-1", "db-1", "col-1", largeData)

        assertThat(op.dataJson).contains("field50")
        assertThat(op.dataJson).contains("value50")
    }

    @Test
    fun `serializes unicode strings to JSON`() {
        val op = PendingOperation.create(
            "doc-1", "db-1", "col-1",
            mapOf("text" to "Hello World")
        )

        assertThat(op.dataJson).contains("Hello World")
    }
}
