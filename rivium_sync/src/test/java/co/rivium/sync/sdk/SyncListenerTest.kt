package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.offline.ConflictInfo
import co.rivium.sync.sdk.offline.OperationType
import org.junit.Test

/**
 * Tests for the SyncListener interface.
 *
 * These tests verify that mock implementations can properly implement all
 * callback methods and that they receive the expected arguments.
 */
class SyncListenerTest {

    // ==================== Mock Implementation ====================

    /**
     * Mock SyncListener that records all callback invocations.
     */
    private class MockSyncListener : SyncListener {
        var syncStartedCount = 0
        var syncCompletedCount = 0
        var syncFailedCount = 0
        var conflictDetectedCount = 0
        var documentSyncedCount = 0

        val completedCounts = mutableListOf<Int>()
        val failedErrors = mutableListOf<Throwable>()
        val detectedConflicts = mutableListOf<ConflictInfo>()
        val syncedDocuments = mutableListOf<Pair<String, OperationType>>()

        override fun onSyncStarted() {
            syncStartedCount++
        }

        override fun onSyncCompleted(syncedCount: Int) {
            syncCompletedCount++
            completedCounts.add(syncedCount)
        }

        override fun onSyncFailed(error: Throwable) {
            syncFailedCount++
            failedErrors.add(error)
        }

        override fun onConflictDetected(conflict: ConflictInfo) {
            conflictDetectedCount++
            detectedConflicts.add(conflict)
        }

        override fun onDocumentSynced(documentId: String, operation: OperationType) {
            documentSyncedCount++
            syncedDocuments.add(Pair(documentId, operation))
        }
    }

    // ==================== Interface Compliance ====================

    @Test
    fun `MockSyncListener implements SyncListener`() {
        val listener: SyncListener = MockSyncListener()

        assertThat(listener).isInstanceOf(SyncListener::class.java)
    }

    // ==================== onSyncStarted Tests ====================

    @Test
    fun `onSyncStarted can be invoked`() {
        val listener = MockSyncListener()

        listener.onSyncStarted()

        assertThat(listener.syncStartedCount).isEqualTo(1)
    }

    @Test
    fun `onSyncStarted can be invoked multiple times`() {
        val listener = MockSyncListener()

        listener.onSyncStarted()
        listener.onSyncStarted()
        listener.onSyncStarted()

        assertThat(listener.syncStartedCount).isEqualTo(3)
    }

    // ==================== onSyncCompleted Tests ====================

    @Test
    fun `onSyncCompleted receives synced count`() {
        val listener = MockSyncListener()

        listener.onSyncCompleted(42)

        assertThat(listener.syncCompletedCount).isEqualTo(1)
        assertThat(listener.completedCounts).containsExactly(42)
    }

    @Test
    fun `onSyncCompleted with zero documents`() {
        val listener = MockSyncListener()

        listener.onSyncCompleted(0)

        assertThat(listener.completedCounts).containsExactly(0)
    }

    @Test
    fun `onSyncCompleted with large count`() {
        val listener = MockSyncListener()

        listener.onSyncCompleted(10_000)

        assertThat(listener.completedCounts).containsExactly(10_000)
    }

    @Test
    fun `onSyncCompleted accumulates across multiple calls`() {
        val listener = MockSyncListener()

        listener.onSyncCompleted(5)
        listener.onSyncCompleted(10)
        listener.onSyncCompleted(3)

        assertThat(listener.syncCompletedCount).isEqualTo(3)
        assertThat(listener.completedCounts).containsExactly(5, 10, 3).inOrder()
    }

    // ==================== onSyncFailed Tests ====================

    @Test
    fun `onSyncFailed receives error`() {
        val listener = MockSyncListener()
        val error = RuntimeException("Network timeout")

        listener.onSyncFailed(error)

        assertThat(listener.syncFailedCount).isEqualTo(1)
        assertThat(listener.failedErrors).hasSize(1)
        assertThat(listener.failedErrors[0].message).isEqualTo("Network timeout")
    }

    @Test
    fun `onSyncFailed with different exception types`() {
        val listener = MockSyncListener()

        listener.onSyncFailed(RuntimeException("runtime error"))
        listener.onSyncFailed(IllegalStateException("illegal state"))
        listener.onSyncFailed(IllegalArgumentException("bad argument"))

        assertThat(listener.syncFailedCount).isEqualTo(3)
        assertThat(listener.failedErrors[0]).isInstanceOf(RuntimeException::class.java)
        assertThat(listener.failedErrors[1]).isInstanceOf(IllegalStateException::class.java)
        assertThat(listener.failedErrors[2]).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `onSyncFailed preserves error cause chain`() {
        val listener = MockSyncListener()
        val rootCause = java.io.IOException("connection refused")
        val error = RuntimeException("Sync failed", rootCause)

        listener.onSyncFailed(error)

        assertThat(listener.failedErrors[0].cause).isEqualTo(rootCause)
        assertThat(listener.failedErrors[0].cause!!.message).isEqualTo("connection refused")
    }

    @Test
    fun `onSyncFailed with null message`() {
        val listener = MockSyncListener()
        val error = RuntimeException(null as String?)

        listener.onSyncFailed(error)

        assertThat(listener.syncFailedCount).isEqualTo(1)
        assertThat(listener.failedErrors[0].message).isNull()
    }

    // ==================== onConflictDetected Tests ====================

    @Test
    fun `onConflictDetected receives conflict info`() {
        val listener = MockSyncListener()
        val conflict = ConflictInfo(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = mapOf("name" to "Local Name"),
            serverData = mapOf("name" to "Server Name"),
            localVersion = 2,
            serverVersion = 3
        )

        listener.onConflictDetected(conflict)

        assertThat(listener.conflictDetectedCount).isEqualTo(1)
        assertThat(listener.detectedConflicts).hasSize(1)
        assertThat(listener.detectedConflicts[0].documentId).isEqualTo("doc-1")
        assertThat(listener.detectedConflicts[0].databaseId).isEqualTo("db-1")
        assertThat(listener.detectedConflicts[0].collectionId).isEqualTo("col-1")
    }

    @Test
    fun `onConflictDetected preserves local and server data`() {
        val listener = MockSyncListener()
        val localData = mapOf("name" to "Alice", "age" to 30)
        val serverData = mapOf("name" to "Alice B.", "age" to 31)
        val conflict = ConflictInfo(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = localData,
            serverData = serverData,
            localVersion = 5,
            serverVersion = 6
        )

        listener.onConflictDetected(conflict)

        assertThat(listener.detectedConflicts[0].localData).isEqualTo(localData)
        assertThat(listener.detectedConflicts[0].serverData).isEqualTo(serverData)
    }

    @Test
    fun `onConflictDetected preserves version numbers`() {
        val listener = MockSyncListener()
        val conflict = ConflictInfo(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = emptyMap(),
            serverData = emptyMap(),
            localVersion = 10,
            serverVersion = 12
        )

        listener.onConflictDetected(conflict)

        assertThat(listener.detectedConflicts[0].localVersion).isEqualTo(10)
        assertThat(listener.detectedConflicts[0].serverVersion).isEqualTo(12)
    }

    @Test
    fun `onConflictDetected with empty data maps`() {
        val listener = MockSyncListener()
        val conflict = ConflictInfo(
            documentId = "doc-empty",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = emptyMap(),
            serverData = emptyMap(),
            localVersion = 1,
            serverVersion = 2
        )

        listener.onConflictDetected(conflict)

        assertThat(listener.detectedConflicts[0].localData).isEmpty()
        assertThat(listener.detectedConflicts[0].serverData).isEmpty()
    }

    @Test
    fun `onConflictDetected with complex nested data`() {
        val listener = MockSyncListener()
        val localData = mapOf(
            "profile" to mapOf(
                "name" to "John",
                "preferences" to mapOf("theme" to "dark")
            ),
            "tags" to listOf("admin", "verified")
        )
        val serverData = mapOf(
            "profile" to mapOf(
                "name" to "John D.",
                "preferences" to mapOf("theme" to "light")
            ),
            "tags" to listOf("admin", "verified", "premium")
        )
        val conflict = ConflictInfo(
            documentId = "doc-nested",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = localData,
            serverData = serverData,
            localVersion = 3,
            serverVersion = 4
        )

        listener.onConflictDetected(conflict)

        assertThat(listener.detectedConflicts[0].localData).containsKey("profile")
        assertThat(listener.detectedConflicts[0].serverData).containsKey("tags")
    }

    @Test
    fun `onConflictDetected accumulates multiple conflicts`() {
        val listener = MockSyncListener()

        for (i in 1..5) {
            listener.onConflictDetected(ConflictInfo(
                documentId = "doc-$i",
                databaseId = "db-1",
                collectionId = "col-1",
                localData = mapOf("index" to i),
                serverData = mapOf("index" to i * 10),
                localVersion = i,
                serverVersion = i + 1
            ))
        }

        assertThat(listener.conflictDetectedCount).isEqualTo(5)
        assertThat(listener.detectedConflicts).hasSize(5)
        assertThat(listener.detectedConflicts[0].documentId).isEqualTo("doc-1")
        assertThat(listener.detectedConflicts[4].documentId).isEqualTo("doc-5")
    }

    // ==================== onDocumentSynced Tests ====================

    @Test
    fun `onDocumentSynced with CREATE operation`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("doc-new", OperationType.CREATE)

        assertThat(listener.documentSyncedCount).isEqualTo(1)
        assertThat(listener.syncedDocuments[0].first).isEqualTo("doc-new")
        assertThat(listener.syncedDocuments[0].second).isEqualTo(OperationType.CREATE)
    }

    @Test
    fun `onDocumentSynced with UPDATE operation`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("doc-existing", OperationType.UPDATE)

        assertThat(listener.documentSyncedCount).isEqualTo(1)
        assertThat(listener.syncedDocuments[0].first).isEqualTo("doc-existing")
        assertThat(listener.syncedDocuments[0].second).isEqualTo(OperationType.UPDATE)
    }

    @Test
    fun `onDocumentSynced with DELETE operation`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("doc-deleted", OperationType.DELETE)

        assertThat(listener.documentSyncedCount).isEqualTo(1)
        assertThat(listener.syncedDocuments[0].first).isEqualTo("doc-deleted")
        assertThat(listener.syncedDocuments[0].second).isEqualTo(OperationType.DELETE)
    }

    @Test
    fun `onDocumentSynced with all operation types`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("doc-1", OperationType.CREATE)
        listener.onDocumentSynced("doc-2", OperationType.UPDATE)
        listener.onDocumentSynced("doc-3", OperationType.DELETE)

        assertThat(listener.documentSyncedCount).isEqualTo(3)
        assertThat(listener.syncedDocuments.map { it.second }).containsExactly(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.DELETE
        ).inOrder()
    }

    @Test
    fun `onDocumentSynced tracks document IDs in order`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("alpha", OperationType.CREATE)
        listener.onDocumentSynced("bravo", OperationType.UPDATE)
        listener.onDocumentSynced("charlie", OperationType.DELETE)
        listener.onDocumentSynced("delta", OperationType.CREATE)

        assertThat(listener.syncedDocuments.map { it.first }).containsExactly(
            "alpha", "bravo", "charlie", "delta"
        ).inOrder()
    }

    @Test
    fun `onDocumentSynced same document multiple times`() {
        val listener = MockSyncListener()

        listener.onDocumentSynced("doc-1", OperationType.CREATE)
        listener.onDocumentSynced("doc-1", OperationType.UPDATE)
        listener.onDocumentSynced("doc-1", OperationType.UPDATE)
        listener.onDocumentSynced("doc-1", OperationType.DELETE)

        assertThat(listener.documentSyncedCount).isEqualTo(4)
        assertThat(listener.syncedDocuments.map { it.second }).containsExactly(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.UPDATE,
            OperationType.DELETE
        ).inOrder()
    }

    // ==================== OperationType Enum Tests ====================

    @Test
    fun `OperationType has exactly 3 entries`() {
        assertThat(OperationType.entries).hasSize(3)
    }

    @Test
    fun `OperationType contains CREATE, UPDATE, DELETE`() {
        assertThat(OperationType.entries).containsExactly(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.DELETE
        )
    }

    @Test
    fun `OperationType valueOf resolves all members`() {
        assertThat(OperationType.valueOf("CREATE")).isEqualTo(OperationType.CREATE)
        assertThat(OperationType.valueOf("UPDATE")).isEqualTo(OperationType.UPDATE)
        assertThat(OperationType.valueOf("DELETE")).isEqualTo(OperationType.DELETE)
    }

    // ==================== Full Sync Lifecycle Simulation ====================

    @Test
    fun `full sync lifecycle with single listener`() {
        val listener = MockSyncListener()

        // Sync starts
        listener.onSyncStarted()

        // Documents are synced
        listener.onDocumentSynced("doc-1", OperationType.CREATE)
        listener.onDocumentSynced("doc-2", OperationType.UPDATE)

        // A conflict is detected
        listener.onConflictDetected(ConflictInfo(
            documentId = "doc-3",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = mapOf("value" to "local"),
            serverData = mapOf("value" to "server"),
            localVersion = 2,
            serverVersion = 3
        ))

        // More documents synced after conflict resolution
        listener.onDocumentSynced("doc-3", OperationType.UPDATE)

        // Sync completes
        listener.onSyncCompleted(3)

        assertThat(listener.syncStartedCount).isEqualTo(1)
        assertThat(listener.documentSyncedCount).isEqualTo(3)
        assertThat(listener.conflictDetectedCount).isEqualTo(1)
        assertThat(listener.syncCompletedCount).isEqualTo(1)
        assertThat(listener.completedCounts[0]).isEqualTo(3)
        assertThat(listener.syncFailedCount).isEqualTo(0)
    }

    @Test
    fun `failed sync lifecycle with single listener`() {
        val listener = MockSyncListener()

        // Sync starts
        listener.onSyncStarted()

        // One document synced before failure
        listener.onDocumentSynced("doc-1", OperationType.CREATE)

        // Sync fails
        listener.onSyncFailed(RuntimeException("Server unavailable"))

        assertThat(listener.syncStartedCount).isEqualTo(1)
        assertThat(listener.documentSyncedCount).isEqualTo(1)
        assertThat(listener.syncFailedCount).isEqualTo(1)
        assertThat(listener.syncCompletedCount).isEqualTo(0)
        assertThat(listener.failedErrors[0].message).isEqualTo("Server unavailable")
    }

    // ==================== Multiple Listeners Pattern ====================

    @Test
    fun `multiple listeners all receive onSyncStarted`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listener3 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2, listener3)

        listeners.forEach { it.onSyncStarted() }

        assertThat(listener1.syncStartedCount).isEqualTo(1)
        assertThat(listener2.syncStartedCount).isEqualTo(1)
        assertThat(listener3.syncStartedCount).isEqualTo(1)
    }

    @Test
    fun `multiple listeners all receive onSyncCompleted`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2)

        listeners.forEach { it.onSyncCompleted(15) }

        assertThat(listener1.completedCounts).containsExactly(15)
        assertThat(listener2.completedCounts).containsExactly(15)
    }

    @Test
    fun `multiple listeners all receive onSyncFailed`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2)
        val error = RuntimeException("Timeout")

        listeners.forEach { it.onSyncFailed(error) }

        assertThat(listener1.failedErrors).hasSize(1)
        assertThat(listener2.failedErrors).hasSize(1)
        assertThat(listener1.failedErrors[0]).isSameInstanceAs(error)
        assertThat(listener2.failedErrors[0]).isSameInstanceAs(error)
    }

    @Test
    fun `multiple listeners all receive onConflictDetected`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2)
        val conflict = ConflictInfo(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            localData = mapOf("a" to 1),
            serverData = mapOf("a" to 2),
            localVersion = 1,
            serverVersion = 2
        )

        listeners.forEach { it.onConflictDetected(conflict) }

        assertThat(listener1.detectedConflicts).hasSize(1)
        assertThat(listener2.detectedConflicts).hasSize(1)
        assertThat(listener1.detectedConflicts[0]).isSameInstanceAs(conflict)
        assertThat(listener2.detectedConflicts[0]).isSameInstanceAs(conflict)
    }

    @Test
    fun `multiple listeners all receive onDocumentSynced`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listener3 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2, listener3)

        listeners.forEach { it.onDocumentSynced("doc-abc", OperationType.UPDATE) }

        assertThat(listener1.syncedDocuments).hasSize(1)
        assertThat(listener2.syncedDocuments).hasSize(1)
        assertThat(listener3.syncedDocuments).hasSize(1)
        assertThat(listener1.syncedDocuments[0].first).isEqualTo("doc-abc")
        assertThat(listener2.syncedDocuments[0].second).isEqualTo(OperationType.UPDATE)
    }

    @Test
    fun `multiple listeners full lifecycle simulation`() {
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()
        val listeners = listOf<SyncListener>(listener1, listener2)

        // Broadcast sync started
        listeners.forEach { it.onSyncStarted() }

        // Broadcast document synced
        listeners.forEach { it.onDocumentSynced("doc-1", OperationType.CREATE) }
        listeners.forEach { it.onDocumentSynced("doc-2", OperationType.UPDATE) }

        // Broadcast sync completed
        listeners.forEach { it.onSyncCompleted(2) }

        // Verify both listeners received all events
        for (listener in listOf(listener1, listener2)) {
            assertThat(listener.syncStartedCount).isEqualTo(1)
            assertThat(listener.documentSyncedCount).isEqualTo(2)
            assertThat(listener.syncCompletedCount).isEqualTo(1)
            assertThat(listener.completedCounts[0]).isEqualTo(2)
            assertThat(listener.syncFailedCount).isEqualTo(0)
            assertThat(listener.conflictDetectedCount).isEqualTo(0)
        }
    }

    @Test
    fun `listeners can be added and removed from a mutable list`() {
        val listeners = mutableListOf<SyncListener>()
        val listener1 = MockSyncListener()
        val listener2 = MockSyncListener()

        // Add first listener
        listeners.add(listener1)
        listeners.forEach { it.onSyncStarted() }

        // Add second listener
        listeners.add(listener2)
        listeners.forEach { it.onDocumentSynced("doc-1", OperationType.CREATE) }

        // Remove first listener
        listeners.remove(listener1)
        listeners.forEach { it.onSyncCompleted(1) }

        // listener1 got started + documentSynced, but not completed
        assertThat(listener1.syncStartedCount).isEqualTo(1)
        assertThat(listener1.documentSyncedCount).isEqualTo(1)
        assertThat(listener1.syncCompletedCount).isEqualTo(0)

        // listener2 got documentSynced + completed, but not started
        assertThat(listener2.syncStartedCount).isEqualTo(0)
        assertThat(listener2.documentSyncedCount).isEqualTo(1)
        assertThat(listener2.syncCompletedCount).isEqualTo(1)
    }

    // ==================== ConflictInfo Data Class Tests ====================

    @Test
    fun `ConflictInfo holds all fields correctly`() {
        val conflict = ConflictInfo(
            documentId = "doc-xyz",
            databaseId = "db-abc",
            collectionId = "col-def",
            localData = mapOf("key" to "local-value"),
            serverData = mapOf("key" to "server-value"),
            localVersion = 7,
            serverVersion = 9
        )

        assertThat(conflict.documentId).isEqualTo("doc-xyz")
        assertThat(conflict.databaseId).isEqualTo("db-abc")
        assertThat(conflict.collectionId).isEqualTo("col-def")
        assertThat(conflict.localData["key"]).isEqualTo("local-value")
        assertThat(conflict.serverData["key"]).isEqualTo("server-value")
        assertThat(conflict.localVersion).isEqualTo(7)
        assertThat(conflict.serverVersion).isEqualTo(9)
    }

    @Test
    fun `ConflictInfo equality`() {
        val conflict1 = ConflictInfo("doc-1", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)
        val conflict2 = ConflictInfo("doc-1", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)
        val conflict3 = ConflictInfo("doc-2", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)

        assertThat(conflict1).isEqualTo(conflict2)
        assertThat(conflict1).isNotEqualTo(conflict3)
    }

    @Test
    fun `ConflictInfo hashCode is consistent with equals`() {
        val conflict1 = ConflictInfo("doc-1", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)
        val conflict2 = ConflictInfo("doc-1", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)

        assertThat(conflict1.hashCode()).isEqualTo(conflict2.hashCode())
    }

    @Test
    fun `ConflictInfo can be copied with modifications`() {
        val original = ConflictInfo("doc-1", "db-1", "col-1", mapOf("a" to 1), mapOf("a" to 2), 1, 2)
        val copied = original.copy(documentId = "doc-2", serverVersion = 5)

        assertThat(copied.documentId).isEqualTo("doc-2")
        assertThat(copied.serverVersion).isEqualTo(5)
        assertThat(copied.databaseId).isEqualTo("db-1")
        assertThat(original.documentId).isEqualTo("doc-1")
    }
}
