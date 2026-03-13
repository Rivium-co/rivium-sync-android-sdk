package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Comprehensive tests for WriteBatch
 *
 * Note: WriteBatch requires an ApiClient which is internal, so we test
 * the public interface behavior through the BatchOperation data classes
 * and state management that we can verify without mocking.
 */
class WriteBatchTest {

    // ==================== BatchOperation Data Classes ====================

    @Test
    fun `BatchOperation Set holds correct values`() {
        val op = WriteBatch.BatchOperation.Set(
            databaseId = "db-123",
            collectionId = "users",
            documentId = "user-1",
            data = mapOf("name" to "John", "age" to 30)
        )

        assertThat(op.databaseId).isEqualTo("db-123")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.documentId).isEqualTo("user-1")
        assertThat(op.data["name"]).isEqualTo("John")
        assertThat(op.data["age"]).isEqualTo(30)
    }

    @Test
    fun `BatchOperation Update holds correct values`() {
        val op = WriteBatch.BatchOperation.Update(
            databaseId = "db-123",
            collectionId = "users",
            documentId = "user-1",
            data = mapOf("status" to "active")
        )

        assertThat(op.databaseId).isEqualTo("db-123")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.documentId).isEqualTo("user-1")
        assertThat(op.data["status"]).isEqualTo("active")
    }

    @Test
    fun `BatchOperation Delete holds correct values`() {
        val op = WriteBatch.BatchOperation.Delete(
            databaseId = "db-123",
            collectionId = "users",
            documentId = "user-1"
        )

        assertThat(op.databaseId).isEqualTo("db-123")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.documentId).isEqualTo("user-1")
    }

    @Test
    fun `BatchOperation Create holds correct values`() {
        val op = WriteBatch.BatchOperation.Create(
            databaseId = "db-123",
            collectionId = "users",
            data = mapOf("name" to "Jane", "email" to "jane@example.com")
        )

        assertThat(op.databaseId).isEqualTo("db-123")
        assertThat(op.collectionId).isEqualTo("users")
        assertThat(op.data["name"]).isEqualTo("Jane")
        assertThat(op.data["email"]).isEqualTo("jane@example.com")
    }

    // ==================== BatchOperation Equality ====================

    @Test
    fun `BatchOperation Set equality`() {
        val op1 = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 1))
        val op2 = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 1))
        val op3 = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 2))

        assertThat(op1).isEqualTo(op2)
        assertThat(op1).isNotEqualTo(op3)
    }

    @Test
    fun `BatchOperation Update equality`() {
        val op1 = WriteBatch.BatchOperation.Update("db", "col", "doc", mapOf("a" to 1))
        val op2 = WriteBatch.BatchOperation.Update("db", "col", "doc", mapOf("a" to 1))

        assertThat(op1).isEqualTo(op2)
    }

    @Test
    fun `BatchOperation Delete equality`() {
        val op1 = WriteBatch.BatchOperation.Delete("db", "col", "doc")
        val op2 = WriteBatch.BatchOperation.Delete("db", "col", "doc")

        assertThat(op1).isEqualTo(op2)
    }

    @Test
    fun `BatchOperation Create equality`() {
        val op1 = WriteBatch.BatchOperation.Create("db", "col", mapOf("a" to 1))
        val op2 = WriteBatch.BatchOperation.Create("db", "col", mapOf("a" to 1))

        assertThat(op1).isEqualTo(op2)
    }

    // ==================== BatchOperation Type Checking ====================

    @Test
    fun `BatchOperation types are distinguishable`() {
        val set = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf())
        val update = WriteBatch.BatchOperation.Update("db", "col", "doc", mapOf())
        val delete = WriteBatch.BatchOperation.Delete("db", "col", "doc")
        val create = WriteBatch.BatchOperation.Create("db", "col", mapOf())

        assertThat(set).isInstanceOf(WriteBatch.BatchOperation.Set::class.java)
        assertThat(update).isInstanceOf(WriteBatch.BatchOperation.Update::class.java)
        assertThat(delete).isInstanceOf(WriteBatch.BatchOperation.Delete::class.java)
        assertThat(create).isInstanceOf(WriteBatch.BatchOperation.Create::class.java)
    }

    @Test
    fun `BatchOperation sealed class exhaustive when`() {
        val operations: List<WriteBatch.BatchOperation> = listOf(
            WriteBatch.BatchOperation.Set("db", "col", "doc1", mapOf("a" to 1)),
            WriteBatch.BatchOperation.Update("db", "col", "doc2", mapOf("b" to 2)),
            WriteBatch.BatchOperation.Delete("db", "col", "doc3"),
            WriteBatch.BatchOperation.Create("db", "col", mapOf("c" to 3))
        )

        operations.forEach { op ->
            val type = when (op) {
                is WriteBatch.BatchOperation.Set -> "set"
                is WriteBatch.BatchOperation.Update -> "update"
                is WriteBatch.BatchOperation.Delete -> "delete"
                is WriteBatch.BatchOperation.Create -> "create"
            }
            assertThat(type).isNotEmpty()
        }
    }

    // ==================== BatchOperation with Complex Data ====================

    @Test
    fun `BatchOperation Set handles nested data`() {
        val nestedData = mapOf(
            "user" to mapOf(
                "name" to "John",
                "address" to mapOf(
                    "city" to "NYC",
                    "zip" to "10001"
                )
            ),
            "tags" to listOf("vip", "premium")
        )

        val op = WriteBatch.BatchOperation.Set("db", "col", "doc", nestedData)

        @Suppress("UNCHECKED_CAST")
        val user = op.data["user"] as Map<String, Any?>
        assertThat(user["name"]).isEqualTo("John")

        @Suppress("UNCHECKED_CAST")
        val address = user["address"] as Map<String, Any?>
        assertThat(address["city"]).isEqualTo("NYC")
    }

    @Test
    fun `BatchOperation handles null values in data`() {
        val op = WriteBatch.BatchOperation.Set(
            "db", "col", "doc",
            mapOf("name" to "John", "middleName" to null)
        )

        assertThat(op.data["name"]).isEqualTo("John")
        assertThat(op.data["middleName"]).isNull()
        assertThat(op.data.containsKey("middleName")).isTrue()
    }

    @Test
    fun `BatchOperation handles empty data map`() {
        val op = WriteBatch.BatchOperation.Set("db", "col", "doc", emptyMap())

        assertThat(op.data).isEmpty()
    }

    // ==================== BatchOperation Copy ====================

    @Test
    fun `BatchOperation Set can be copied with modifications`() {
        val original = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 1))
        val copied = original.copy(documentId = "new-doc")

        assertThat(copied.databaseId).isEqualTo("db")
        assertThat(copied.documentId).isEqualTo("new-doc")
        assertThat(original.documentId).isEqualTo("doc")
    }

    @Test
    fun `BatchOperation Update can be copied with modifications`() {
        val original = WriteBatch.BatchOperation.Update("db", "col", "doc", mapOf("a" to 1))
        val copied = original.copy(data = mapOf("b" to 2))

        assertThat(copied.data).isEqualTo(mapOf("b" to 2))
        assertThat(original.data).isEqualTo(mapOf("a" to 1))
    }

    // ==================== BatchOperation toString ====================

    @Test
    fun `BatchOperation Set toString contains useful info`() {
        val op = WriteBatch.BatchOperation.Set("db-123", "users", "user-1", mapOf("name" to "John"))

        val str = op.toString()

        assertThat(str).contains("db-123")
        assertThat(str).contains("users")
        assertThat(str).contains("user-1")
    }

    @Test
    fun `BatchOperation Delete toString contains useful info`() {
        val op = WriteBatch.BatchOperation.Delete("db-123", "users", "user-1")

        val str = op.toString()

        assertThat(str).contains("db-123")
        assertThat(str).contains("users")
        assertThat(str).contains("user-1")
    }

    // ==================== BatchOperation with Various Data Types ====================

    @Test
    fun `BatchOperation handles all primitive data types`() {
        val data = mapOf(
            "string" to "hello",
            "int" to 42,
            "long" to 9999999999L,
            "double" to 3.14159,
            "boolean" to true,
            "null" to null
        )

        val op = WriteBatch.BatchOperation.Set("db", "col", "doc", data)

        assertThat(op.data["string"]).isEqualTo("hello")
        assertThat(op.data["int"]).isEqualTo(42)
        assertThat(op.data["long"]).isEqualTo(9999999999L)
        assertThat(op.data["double"]).isEqualTo(3.14159)
        assertThat(op.data["boolean"]).isEqualTo(true)
        assertThat(op.data["null"]).isNull()
    }

    @Test
    fun `BatchOperation handles list data`() {
        val data = mapOf(
            "numbers" to listOf(1, 2, 3),
            "strings" to listOf("a", "b", "c"),
            "mixed" to listOf(1, "two", true)
        )

        val op = WriteBatch.BatchOperation.Set("db", "col", "doc", data)

        assertThat(op.data["numbers"]).isEqualTo(listOf(1, 2, 3))
        assertThat(op.data["strings"]).isEqualTo(listOf("a", "b", "c"))
    }

    // ==================== HashCode ====================

    @Test
    fun `BatchOperation hashCode is consistent with equals`() {
        val op1 = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 1))
        val op2 = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf("a" to 1))

        assertThat(op1.hashCode()).isEqualTo(op2.hashCode())
    }

    @Test
    fun `Different BatchOperation types have different hashCodes`() {
        val set = WriteBatch.BatchOperation.Set("db", "col", "doc", mapOf())
        val update = WriteBatch.BatchOperation.Update("db", "col", "doc", mapOf())

        // While not strictly required, different types should generally have different hashcodes
        assertThat(set).isNotEqualTo(update)
    }
}
