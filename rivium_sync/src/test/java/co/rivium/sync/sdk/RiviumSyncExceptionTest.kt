package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for RiviumSyncException sealed class hierarchy
 */
class RiviumSyncExceptionTest {

    @Test
    fun `NotInitializedException has correct message`() {
        val exception = RiviumSyncException.NotInitializedException()

        assertThat(exception.message).contains("not initialized")
        assertThat(exception.message).contains("RiviumSync.initialize()")
    }

    @Test
    fun `NetworkException contains message`() {
        val exception = RiviumSyncException.NetworkException("Connection failed")

        assertThat(exception.message).isEqualTo("Connection failed")
    }

    @Test
    fun `NetworkException contains cause`() {
        val cause = RuntimeException("Timeout")
        val exception = RiviumSyncException.NetworkException("Connection failed", cause)

        assertThat(exception.message).isEqualTo("Connection failed")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `AuthenticationException contains message`() {
        val exception = RiviumSyncException.AuthenticationException("Invalid API key")

        assertThat(exception.message).isEqualTo("Invalid API key")
    }

    @Test
    fun `DatabaseException contains message`() {
        val exception = RiviumSyncException.DatabaseException("Database not found")

        assertThat(exception.message).isEqualTo("Database not found")
    }

    @Test
    fun `CollectionException contains message`() {
        val exception = RiviumSyncException.CollectionException("Collection already exists")

        assertThat(exception.message).isEqualTo("Collection already exists")
    }

    @Test
    fun `DocumentException contains message`() {
        val exception = RiviumSyncException.DocumentException("Document not found")

        assertThat(exception.message).isEqualTo("Document not found")
    }

    @Test
    fun `ConnectionException contains message and cause`() {
        val cause = java.net.SocketTimeoutException()
        val exception = RiviumSyncException.ConnectionException("MQTT connection failed", cause)

        assertThat(exception.message).isEqualTo("MQTT connection failed")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `TimeoutException contains message`() {
        val exception = RiviumSyncException.TimeoutException("Operation timed out after 30s")

        assertThat(exception.message).isEqualTo("Operation timed out after 30s")
    }

    @Test
    fun `PermissionException contains message`() {
        val exception = RiviumSyncException.PermissionException("Write access denied")

        assertThat(exception.message).isEqualTo("Write access denied")
    }

    @Test
    fun `BatchWriteException contains message and cause`() {
        val cause = RuntimeException("Partial failure")
        val exception = RiviumSyncException.BatchWriteException("Batch commit failed", cause)

        assertThat(exception.message).isEqualTo("Batch commit failed")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `TransactionException contains message and cause`() {
        val cause = RuntimeException("Conflict detected")
        val exception = RiviumSyncException.TransactionException("Transaction aborted", cause)

        assertThat(exception.message).isEqualTo("Transaction aborted")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `All exceptions are subclasses of RiviumSyncException`() {
        val exceptions = listOf<RiviumSyncException>(
            RiviumSyncException.NotInitializedException(),
            RiviumSyncException.NetworkException("test"),
            RiviumSyncException.AuthenticationException("test"),
            RiviumSyncException.DatabaseException("test"),
            RiviumSyncException.CollectionException("test"),
            RiviumSyncException.DocumentException("test"),
            RiviumSyncException.ConnectionException("test"),
            RiviumSyncException.TimeoutException("test"),
            RiviumSyncException.PermissionException("test"),
            RiviumSyncException.BatchWriteException("test"),
            RiviumSyncException.TransactionException("test")
        )

        exceptions.forEach { exception ->
            assertThat(exception).isInstanceOf(RiviumSyncException::class.java)
            assertThat(exception).isInstanceOf(Exception::class.java)
        }
    }

    @Test
    fun `Exceptions can be used in exhaustive when expressions`() {
        val exceptions = listOf<RiviumSyncException>(
            RiviumSyncException.NotInitializedException(),
            RiviumSyncException.NetworkException("test"),
            RiviumSyncException.AuthenticationException("test"),
            RiviumSyncException.DatabaseException("test"),
            RiviumSyncException.CollectionException("test"),
            RiviumSyncException.DocumentException("test"),
            RiviumSyncException.ConnectionException("test"),
            RiviumSyncException.TimeoutException("test"),
            RiviumSyncException.PermissionException("test"),
            RiviumSyncException.BatchWriteException("test"),
            RiviumSyncException.TransactionException("test")
        )

        exceptions.forEach { exception ->
            val errorType = when (exception) {
                is RiviumSyncException.NotInitializedException -> "init"
                is RiviumSyncException.NetworkException -> "network"
                is RiviumSyncException.AuthenticationException -> "auth"
                is RiviumSyncException.DatabaseException -> "database"
                is RiviumSyncException.CollectionException -> "collection"
                is RiviumSyncException.DocumentException -> "document"
                is RiviumSyncException.ConnectionException -> "connection"
                is RiviumSyncException.TimeoutException -> "timeout"
                is RiviumSyncException.PermissionException -> "permission"
                is RiviumSyncException.BatchWriteException -> "batch"
                is RiviumSyncException.TransactionException -> "transaction"
            }
            assertThat(errorType).isNotEmpty()
        }
    }
}
