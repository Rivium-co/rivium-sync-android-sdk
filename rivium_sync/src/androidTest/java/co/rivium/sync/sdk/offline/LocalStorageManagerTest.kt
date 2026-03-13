package co.rivium.sync.sdk.offline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for LocalStorageManager (Room database)
 *
 * These tests run on an Android device/emulator and verify:
 * - Document CRUD operations
 * - Pending operations queue
 * - Sync status updates
 * - Cache management
 */
@RunWith(AndroidJUnit4::class)
class LocalStorageManagerTest {

    private lateinit var database: RiviumSyncDatabase
    private lateinit var dao: RiviumSyncDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RiviumSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.riviumSyncDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ==================== Document Operations ====================

    private fun createCachedDocument(
        id: String,
        databaseId: String = "db-1",
        collectionId: String = "col-1",
        dataJson: String = """{}""",
        version: Int = 1,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ): CachedDocument {
        val now = System.currentTimeMillis()
        return CachedDocument(
            id = id,
            databaseId = databaseId,
            collectionId = collectionId,
            dataJson = dataJson,
            createdAt = now,
            updatedAt = now,
            version = version,
            syncStatus = syncStatus
        )
    }

    @Test
    fun saveAndRetrieveDocument() = runTest {
        val doc = createCachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"name": "John", "age": 30}""",
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        dao.upsertDocument(doc)
        val retrieved = dao.getDocument("doc-1")

        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.id).isEqualTo("doc-1")
        assertThat(retrieved.databaseId).isEqualTo("db-1")
        assertThat(retrieved.collectionId).isEqualTo("col-1")
        assertThat(retrieved.dataJson).contains("John")
        assertThat(retrieved.version).isEqualTo(1)
        assertThat(retrieved.syncStatus).isEqualTo(SyncStatus.SYNCED)
    }

    @Test
    fun upsertUpdatesExistingDocument() = runTest {
        val doc1 = createCachedDocument(
            id = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            dataJson = """{"name": "John"}""",
            version = 1,
            syncStatus = SyncStatus.SYNCED
        )

        val doc2 = doc1.copy(
            dataJson = """{"name": "Jane"}""",
            version = 2
        )

        dao.upsertDocument(doc1)
        dao.upsertDocument(doc2)

        val retrieved = dao.getDocument("doc-1")
        assertThat(retrieved!!.dataJson).contains("Jane")
        assertThat(retrieved.version).isEqualTo(2)
    }

    @Test
    fun getDocumentsForCollection() = runTest {
        val docs = listOf(
            createCachedDocument("doc-1", "db-1", "col-1"),
            createCachedDocument("doc-2", "db-1", "col-1"),
            createCachedDocument("doc-3", "db-1", "col-2")
        )
        docs.forEach { dao.upsertDocument(it) }

        val col1Docs = dao.getDocuments("db-1", "col-1")
        val col2Docs = dao.getDocuments("db-1", "col-2")

        assertThat(col1Docs).hasSize(2)
        assertThat(col2Docs).hasSize(1)
    }

    @Test
    fun deleteDocument() = runTest {
        val doc = createCachedDocument(id = "doc-1")

        dao.upsertDocument(doc)
        dao.deleteDocument("doc-1")

        val retrieved = dao.getDocument("doc-1")
        assertThat(retrieved).isNull()
    }

    // ==================== Sync Status ====================

    @Test
    fun updateSyncStatus() = runTest {
        val doc = createCachedDocument(
            id = "doc-1",
            syncStatus = SyncStatus.PENDING_CREATE
        )

        dao.upsertDocument(doc)
        dao.updateSyncStatus("doc-1", SyncStatus.SYNCED, null)

        val retrieved = dao.getDocument("doc-1")
        assertThat(retrieved!!.syncStatus).isEqualTo(SyncStatus.SYNCED)
    }

    @Test
    fun getPendingDocuments() = runTest {
        val docs = listOf(
            createCachedDocument("doc-1", syncStatus = SyncStatus.SYNCED),
            createCachedDocument("doc-2", syncStatus = SyncStatus.PENDING_CREATE),
            createCachedDocument("doc-3", syncStatus = SyncStatus.PENDING_UPDATE),
            createCachedDocument("doc-4", syncStatus = SyncStatus.PENDING_DELETE)
        )
        docs.forEach { dao.upsertDocument(it) }

        val pending = dao.getPendingDocuments()

        assertThat(pending).hasSize(3)
        assertThat(pending.map { it.id }).containsExactly("doc-2", "doc-3", "doc-4")
    }

    @Test
    fun getPendingCount() = runTest {
        val docs = listOf(
            createCachedDocument("doc-1", syncStatus = SyncStatus.SYNCED),
            createCachedDocument("doc-2", syncStatus = SyncStatus.PENDING_CREATE),
            createCachedDocument("doc-3", syncStatus = SyncStatus.PENDING_UPDATE)
        )
        docs.forEach { dao.upsertDocument(it) }

        val count = dao.getPendingCount()

        assertThat(count).isEqualTo(2)
    }

    // ==================== Pending Operations Queue ====================

    @Test
    fun insertAndRetrievePendingOperation() = runTest {
        val operation = PendingOperation.create(
            documentId = "doc-1",
            databaseId = "db-1",
            collectionId = "col-1",
            data = mapOf("name" to "John")
        )

        dao.insertOperation(operation)
        val pending = dao.getPendingOperations()

        assertThat(pending).hasSize(1)
        assertThat(pending[0].documentId).isEqualTo("doc-1")
        assertThat(pending[0].operationType).isEqualTo(OperationType.CREATE)
    }

    @Test
    fun pendingOperationsOrderedByCreatedAt() = runTest {
        val op1 = PendingOperation.create("doc-1", "db", "col", mapOf())
        val op2 = PendingOperation.update("doc-2", "db", "col", mapOf(), 1)
        val op3 = PendingOperation.delete("doc-3", "db", "col", 1)

        dao.insertOperation(op1)
        Thread.sleep(10) // Ensure different timestamps
        dao.insertOperation(op2)
        Thread.sleep(10)
        dao.insertOperation(op3)

        val pending = dao.getPendingOperations()

        assertThat(pending).hasSize(3)
        assertThat(pending[0].documentId).isEqualTo("doc-1")
        assertThat(pending[1].documentId).isEqualTo("doc-2")
        assertThat(pending[2].documentId).isEqualTo("doc-3")
    }

    @Test
    fun markOperationProcessing() = runTest {
        val operation = PendingOperation.create("doc-1", "db", "col", mapOf())
        val operationId = dao.insertOperation(operation)

        dao.markOperationProcessing(operationId)

        // Note: getPendingOperations excludes processing operations (WHERE processing = 0)
        // So we verify that the operation is no longer in the pending list
        val pending = dao.getPendingOperations()
        assertThat(pending).isEmpty()
    }

    @Test
    fun markOperationFailed() = runTest {
        val operation = PendingOperation.create("doc-1", "db", "col", mapOf())
        val operationId = dao.insertOperation(operation)

        dao.markOperationFailed(operationId, "Network error")

        val updated = dao.getPendingOperations()
        assertThat(updated).hasSize(1)
        assertThat(updated[0].retryCount).isEqualTo(1)
        assertThat(updated[0].lastError).isEqualTo("Network error")
        assertThat(updated[0].processing).isFalse()
    }

    @Test
    fun deleteOperation() = runTest {
        val operation = PendingOperation.create("doc-1", "db", "col", mapOf())
        val operationId = dao.insertOperation(operation)

        dao.deleteOperationById(operationId)

        val remaining = dao.getPendingOperations()
        assertThat(remaining).isEmpty()
    }

    @Test
    fun deleteOperationsForDocument() = runTest {
        dao.insertOperation(PendingOperation.create("doc-1", "db", "col", mapOf()))
        dao.insertOperation(PendingOperation.update("doc-1", "db", "col", mapOf(), 1))
        dao.insertOperation(PendingOperation.create("doc-2", "db", "col", mapOf()))

        dao.deleteOperationsForDocument("doc-1")

        val pending = dao.getPendingOperations()
        assertThat(pending).hasSize(1)
        assertThat(pending[0].documentId).isEqualTo("doc-2")
    }

    // ==================== Cache Management ====================

    @Test
    fun getCacheSize() = runTest {
        val docs = (1..5).map {
            createCachedDocument("doc-$it")
        }
        docs.forEach { dao.upsertDocument(it) }

        val size = dao.getCacheSize()

        assertThat(size).isEqualTo(5)
    }

    @Test
    fun clearAllDocuments() = runTest {
        val docs = (1..3).map {
            createCachedDocument("doc-$it")
        }
        docs.forEach { dao.upsertDocument(it) }

        dao.clearAllDocuments()

        val size = dao.getCacheSize()
        assertThat(size).isEqualTo(0)
    }

    @Test
    fun clearAllOperations() = runTest {
        dao.insertOperation(PendingOperation.create("doc-1", "db", "col", mapOf()))
        dao.insertOperation(PendingOperation.create("doc-2", "db", "col", mapOf()))

        dao.clearAllOperations()

        val pending = dao.getPendingOperations()
        assertThat(pending).isEmpty()
    }

    @Test
    fun deleteCollection() = runTest {
        dao.upsertDocument(createCachedDocument("doc-1", "db-1", "col-1"))
        dao.upsertDocument(createCachedDocument("doc-2", "db-1", "col-1"))
        dao.upsertDocument(createCachedDocument("doc-3", "db-1", "col-2"))

        dao.deleteCollection("db-1", "col-1")

        val col1Docs = dao.getDocuments("db-1", "col-1")
        val col2Docs = dao.getDocuments("db-1", "col-2")

        assertThat(col1Docs).isEmpty()
        assertThat(col2Docs).hasSize(1)
    }

    @Test
    fun deleteDatabase() = runTest {
        dao.upsertDocument(createCachedDocument("doc-1", "db-1", "col-1"))
        dao.upsertDocument(createCachedDocument("doc-2", "db-1", "col-2"))
        dao.upsertDocument(createCachedDocument("doc-3", "db-2", "col-1"))

        dao.deleteDatabase("db-1")

        val db1Docs = dao.getDocuments("db-1", "col-1")
        val db2Docs = dao.getDocuments("db-2", "col-1")

        assertThat(db1Docs).isEmpty()
        assertThat(db2Docs).hasSize(1)
    }

    // ==================== Flow Tests ====================

    @Test
    fun documentFlowEmitsUpdates() = runTest {
        val doc = createCachedDocument("doc-1", dataJson = """{"v": 1}""")
        dao.upsertDocument(doc)

        val initial = dao.getDocumentFlow("doc-1").first()
        assertThat(initial!!.dataJson).contains("\"v\": 1")

        // Update document
        dao.upsertDocument(doc.copy(dataJson = """{"v": 2}"""))

        val updated = dao.getDocumentFlow("doc-1").first()
        assertThat(updated!!.dataJson).contains("\"v\": 2")
    }

    @Test
    fun pendingCountFlowEmitsUpdates() = runTest {
        val initial = dao.getPendingCountFlow().first()
        assertThat(initial).isEqualTo(0)

        dao.upsertDocument(createCachedDocument("doc-1", syncStatus = SyncStatus.PENDING_CREATE))

        val afterAdd = dao.getPendingCountFlow().first()
        assertThat(afterAdd).isEqualTo(1)
    }
}
