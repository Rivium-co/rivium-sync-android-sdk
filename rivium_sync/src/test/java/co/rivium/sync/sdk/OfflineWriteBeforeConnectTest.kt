package co.rivium.sync.sdk

import co.rivium.sync.sdk.offline.LocalStorageManager
import co.rivium.sync.sdk.offline.SyncEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * With offline persistence on, a write made before the realtime connection has reported anything (the app has not
 * called connect() yet, or it is still connecting) must go to the server — like Firestore, and like reads already do
 * (`isKnownOffline`). Only a connection that has REPORTED offline queues the write locally.
 *
 * Before the fix, add/set/update/delete read `isOnline`, which starts false, so every such write was queued and
 * never sent unless the app connected (found in ScanLeap 2026-10-17: teams stayed on the phone, no error).
 */
class OfflineWriteBeforeConnectTest {
    private val doc = SyncDocument(id = "d1", data = mapOf("name" to "x"), createdAt = 1L, updatedAt = 1L, version = 1)

    /** The engine's own store: a mock that answers the pending-count flow the engine watches. */
    private fun store(): LocalStorageManager = mock<LocalStorageManager>().also {
        whenever(it.getPendingCountFlow()).thenReturn(flowOf(0))
    }

    private fun collection(api: ApiClient, engine: SyncEngine) =
        SyncCollectionImpl("col", "col", "db", api, mock(), mock<LocalStorageManager>(), engine)

    @Test
    fun `add before any connection report goes to the server`() = runTest {
        val api = mock<ApiClient>()
        whenever(api.addDocument(eq("db"), eq("col"), any())).thenReturn(doc)
        val engine = SyncEngine(api, store())

        collection(api, engine).add(mapOf("name" to "x"))

        verify(api).addDocument(eq("db"), eq("col"), any())
    }

    @Test
    fun `set, update and delete before any connection report go to the server`() = runTest {
        val api = mock<ApiClient>()
        whenever(api.setDocument(eq("db"), eq("col"), eq("d1"), any())).thenReturn(doc)
        whenever(api.updateDocument(eq("db"), eq("col"), eq("d1"), any())).thenReturn(doc)
        val engine = SyncEngine(api, store())
        val c = collection(api, engine)

        c.set("d1", mapOf("name" to "x"))
        c.update("d1", mapOf("name" to "y"))
        c.delete("d1")

        verify(api).setDocument(eq("db"), eq("col"), eq("d1"), any())
        verify(api).updateDocument(eq("db"), eq("col"), eq("d1"), any())
        verify(api).deleteDocument(eq("db"), eq("col"), eq("d1"))
    }

    @Test
    fun `a connection that reported offline queues the write instead`() = runTest {
        val api = mock<ApiClient>()
        val engine = SyncEngine(api, store())
        engine.onConnectionStateChanged(false)

        collection(api, engine).add(mapOf("name" to "x"))

        verify(api, never()).addDocument(anyOrNull(), anyOrNull(), anyOrNull())
    }
}
