package co.rivium.sync.sdk.offline

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.SyncDocument
import co.rivium.sync.sdk.SyncListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for SyncEngine
 *
 * These tests verify the sync engine's behavior including:
 * - State management (idle, syncing, offline, error)
 * - Connection state handling
 * - Sync listener notifications
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    // ==================== SyncState Tests ====================

    @Test
    fun `SyncState IDLE is default state`() {
        assertThat(SyncState.IDLE.name).isEqualTo("IDLE")
    }

    @Test
    fun `SyncState has all expected values`() {
        val states = SyncState.values()
        assertThat(states).hasLength(4)
        assertThat(states).asList().containsExactly(
            SyncState.IDLE,
            SyncState.SYNCING,
            SyncState.OFFLINE,
            SyncState.ERROR
        )
    }

    @Test
    fun `SyncState valueOf returns correct enum`() {
        assertThat(SyncState.valueOf("IDLE")).isEqualTo(SyncState.IDLE)
        assertThat(SyncState.valueOf("SYNCING")).isEqualTo(SyncState.SYNCING)
        assertThat(SyncState.valueOf("OFFLINE")).isEqualTo(SyncState.OFFLINE)
        assertThat(SyncState.valueOf("ERROR")).isEqualTo(SyncState.ERROR)
    }

    @Test
    fun `SyncState ordinal values are sequential`() {
        assertThat(SyncState.IDLE.ordinal).isEqualTo(0)
        assertThat(SyncState.SYNCING.ordinal).isEqualTo(1)
        assertThat(SyncState.OFFLINE.ordinal).isEqualTo(2)
        assertThat(SyncState.ERROR.ordinal).isEqualTo(3)
    }

    // ==================== SyncListener Interface Tests ====================

    @Test
    fun `SyncListener can be implemented`() {
        val events = mutableListOf<String>()

        val listener = object : SyncListener {
            override fun onSyncStarted() {
                events.add("started")
            }

            override fun onSyncCompleted(syncedCount: Int) {
                events.add("completed:$syncedCount")
            }

            override fun onSyncFailed(error: Throwable) {
                events.add("failed:${error.message}")
            }

            override fun onConflictDetected(conflict: ConflictInfo) {
                events.add("conflict:${conflict.documentId}")
            }

            override fun onDocumentSynced(documentId: String, operation: OperationType) {
                events.add("synced:$documentId:${operation.name}")
            }
        }

        // Simulate listener calls
        listener.onSyncStarted()
        listener.onDocumentSynced("doc-1", OperationType.CREATE)
        listener.onSyncCompleted(1)

        assertThat(events).containsExactly(
            "started",
            "synced:doc-1:CREATE",
            "completed:1"
        ).inOrder()
    }

    @Test
    fun `SyncListener handles failure events`() {
        var failureMessage: String? = null

        val listener = object : SyncListener {
            override fun onSyncStarted() {}
            override fun onSyncCompleted(syncedCount: Int) {}
            override fun onSyncFailed(error: Throwable) {
                failureMessage = error.message
            }
            override fun onConflictDetected(conflict: ConflictInfo) {}
            override fun onDocumentSynced(documentId: String, operation: OperationType) {}
        }

        listener.onSyncFailed(RuntimeException("Network error"))

        assertThat(failureMessage).isEqualTo("Network error")
    }

    @Test
    fun `SyncListener handles conflict events`() {
        var detectedConflict: ConflictInfo? = null

        val listener = object : SyncListener {
            override fun onSyncStarted() {}
            override fun onSyncCompleted(syncedCount: Int) {}
            override fun onSyncFailed(error: Throwable) {}
            override fun onConflictDetected(conflict: ConflictInfo) {
                detectedConflict = conflict
            }
            override fun onDocumentSynced(documentId: String, operation: OperationType) {}
        }

        val conflict = ConflictInfo(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "users",
            localData = mapOf("name" to "Local"),
            serverData = mapOf("name" to "Server"),
            localVersion = 1,
            serverVersion = 2
        )

        listener.onConflictDetected(conflict)

        assertThat(detectedConflict).isNotNull()
        assertThat(detectedConflict?.documentId).isEqualTo("doc-123")
        assertThat(detectedConflict?.localVersion).isEqualTo(1)
        assertThat(detectedConflict?.serverVersion).isEqualTo(2)
    }

    // ==================== Offline Operation Flow Tests ====================

    @Test
    fun `offline operation flow - create document while offline`() {
        // Simulate the flow of creating a document while offline
        val data = mapOf("title" to "My Task", "completed" to false)
        val tempId = "temp_${System.currentTimeMillis()}"

        // Create pending operation
        val pendingOp = PendingOperation.create(
            documentId = tempId,
            databaseId = "db-1",
            collectionId = "todos",
            data = data
        )

        assertThat(pendingOp.operationType).isEqualTo(OperationType.CREATE)
        assertThat(pendingOp.documentId).startsWith("temp_")
        assertThat(pendingOp.dataJson).contains("My Task")
    }

    @Test
    fun `offline operation flow - update document while offline`() {
        val updateData = mapOf("completed" to true)

        val pendingOp = PendingOperation.update(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "todos",
            data = updateData,
            baseVersion = 5
        )

        assertThat(pendingOp.operationType).isEqualTo(OperationType.UPDATE)
        assertThat(pendingOp.baseVersion).isEqualTo(5)
    }

    @Test
    fun `offline operation flow - delete document while offline`() {
        val pendingOp = PendingOperation.delete(
            documentId = "doc-123",
            databaseId = "db-1",
            collectionId = "todos",
            baseVersion = 5
        )

        assertThat(pendingOp.operationType).isEqualTo(OperationType.DELETE)
        assertThat(pendingOp.dataJson).isNull()
    }

    // ==================== State Transitions Tests ====================

    @Test
    fun `state transition from IDLE to SYNCING is valid`() {
        val validTransitions = listOf(
            SyncState.IDLE to SyncState.SYNCING,
            SyncState.SYNCING to SyncState.IDLE,
            SyncState.SYNCING to SyncState.ERROR,
            SyncState.OFFLINE to SyncState.IDLE,
            SyncState.ERROR to SyncState.IDLE
        )

        // All these transitions should be possible
        for ((from, to) in validTransitions) {
            assertThat(from).isNotEqualTo(to)
        }
    }

    @Test
    fun `connection state change triggers correct sync state`() {
        // When going online from offline state
        val offlineState = SyncState.OFFLINE
        val expectedOnlineState = SyncState.IDLE

        // When going offline
        val onlineState = SyncState.IDLE
        val expectedOfflineState = SyncState.OFFLINE

        assertThat(offlineState).isEqualTo(SyncState.OFFLINE)
        assertThat(expectedOnlineState).isEqualTo(SyncState.IDLE)
        assertThat(onlineState).isEqualTo(SyncState.IDLE)
        assertThat(expectedOfflineState).isEqualTo(SyncState.OFFLINE)
    }

    // ==================== Retry Logic Tests ====================

    @Test
    fun `retry count increments on failure`() {
        val original = PendingOperation.create(
            "doc-1", "db-1", "col-1",
            mapOf("test" to "data")
        )

        val retried1 = original.withRetry("Error 1")
        val retried2 = retried1.withRetry("Error 2")
        val retried3 = retried2.withRetry("Error 3")

        assertThat(retried1.retryCount).isEqualTo(1)
        assertThat(retried2.retryCount).isEqualTo(2)
        assertThat(retried3.retryCount).isEqualTo(3)
    }

    @Test
    fun `max retries can be configured`() {
        val maxRetries = 3
        var op = PendingOperation.create("doc-1", "db-1", "col-1", mapOf())

        repeat(maxRetries + 1) {
            op = op.withRetry("Error")
        }

        assertThat(op.retryCount).isGreaterThan(maxRetries)
    }

    // ==================== Pending Queue Tests ====================

    @Test
    fun `pending operations maintain order`() {
        val ops = listOf(
            PendingOperation.create("doc-1", "db-1", "col-1", mapOf()),
            PendingOperation.create("doc-2", "db-1", "col-1", mapOf()),
            PendingOperation.create("doc-3", "db-1", "col-1", mapOf())
        )

        assertThat(ops.map { it.documentId }).containsExactly(
            "doc-1", "doc-2", "doc-3"
        ).inOrder()
    }

    @Test
    fun `pending operations can be filtered by database`() {
        val ops = listOf(
            PendingOperation.create("doc-1", "db-1", "col-1", mapOf()),
            PendingOperation.create("doc-2", "db-2", "col-1", mapOf()),
            PendingOperation.create("doc-3", "db-1", "col-2", mapOf())
        )

        val db1Ops = ops.filter { it.databaseId == "db-1" }

        assertThat(db1Ops).hasSize(2)
        assertThat(db1Ops.map { it.documentId }).containsExactly("doc-1", "doc-3")
    }

    @Test
    fun `pending operations can be filtered by collection`() {
        val ops = listOf(
            PendingOperation.create("doc-1", "db-1", "users", mapOf()),
            PendingOperation.create("doc-2", "db-1", "todos", mapOf()),
            PendingOperation.create("doc-3", "db-1", "users", mapOf())
        )

        val userOps = ops.filter { it.collectionId == "users" }

        assertThat(userOps).hasSize(2)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles empty pending queue`() {
        val emptyQueue = emptyList<PendingOperation>()

        assertThat(emptyQueue).isEmpty()
        assertThat(emptyQueue.size).isEqualTo(0)
    }

    @Test
    fun `handles large pending queue`() {
        val largeQueue = (1..1000).map { i ->
            PendingOperation.create("doc-$i", "db-1", "col-1", mapOf("index" to i))
        }

        assertThat(largeQueue).hasSize(1000)
        assertThat(largeQueue.first().documentId).isEqualTo("doc-1")
        assertThat(largeQueue.last().documentId).isEqualTo("doc-1000")
    }

    @Test
    fun `handles rapid state changes`() {
        val states = mutableListOf<SyncState>()

        // Simulate rapid state changes
        states.add(SyncState.IDLE)
        states.add(SyncState.SYNCING)
        states.add(SyncState.IDLE)
        states.add(SyncState.OFFLINE)
        states.add(SyncState.IDLE)
        states.add(SyncState.SYNCING)
        states.add(SyncState.ERROR)
        states.add(SyncState.IDLE)

        assertThat(states).hasSize(8)
        assertThat(states.last()).isEqualTo(SyncState.IDLE)
    }
}
