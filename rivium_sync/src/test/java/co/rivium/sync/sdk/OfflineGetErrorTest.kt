package co.rivium.sync.sdk

import co.rivium.sync.sdk.offline.LocalStorageManager
import co.rivium.sync.sdk.offline.SyncEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * With offline persistence on, get() returns null only when the document does not exist. When the server can't be
 * reached (or refuses) and the document is not cached, the error is thrown — before, get() returned null, so apps
 * showed "not found" for a check that existed (ScanLeap on an unstable network, 2026-10-22).
 */
class OfflineGetErrorTest {
    private val doc = SyncDocument(id = "d1", data = mapOf("name" to "x"), createdAt = 1L, updatedAt = 1L, version = 1)

    private fun engineStore(): LocalStorageManager = mock<LocalStorageManager>().also {
        whenever(it.getPendingCountFlow()).thenReturn(flowOf(0))
    }

    private fun collection(api: ApiClient, cache: LocalStorageManager, engine: SyncEngine) =
        SyncCollectionImpl("col", "col", "db", api, mock(), cache, engine)

    @Test
    fun `server error and nothing cached throws the error`() = runTest {
        val api = mock<ApiClient>()
        whenever(api.getDocument("db", "col", "d1")).thenAnswer { throw RiviumSyncException.NetworkException("timeout") }
        val cache = mock<LocalStorageManager>()
        whenever(cache.getDocument("d1")).thenReturn(null)

        try {
            collection(api, cache, SyncEngine(api, engineStore())).get("d1")
            fail("expected the network error")
        } catch (e: RiviumSyncException.NetworkException) {
            assertEquals("timeout", e.message)
        }
    }

    @Test
    fun `server error with a cached copy returns the cached copy`() = runTest {
        val api = mock<ApiClient>()
        whenever(api.getDocument("db", "col", "d1")).thenAnswer { throw RiviumSyncException.NetworkException("timeout") }
        val cache = mock<LocalStorageManager>()
        whenever(cache.getDocument("d1")).thenReturn(doc)

        assertEquals(doc, collection(api, cache, SyncEngine(api, engineStore())).get("d1"))
    }

    @Test
    fun `a document that does not exist is still null`() = runTest {
        val api = mock<ApiClient>()
        whenever(api.getDocument("db", "col", "d1")).thenReturn(null)
        val cache = mock<LocalStorageManager>()
        whenever(cache.getDocument("d1")).thenReturn(null)

        assertNull(collection(api, cache, SyncEngine(api, engineStore())).get("d1"))
    }

    @Test
    fun `known offline and nothing cached throws instead of null`() = runTest {
        val api = mock<ApiClient>()
        val cache = mock<LocalStorageManager>()
        whenever(cache.getDocument("d1")).thenReturn(null)
        val engine = SyncEngine(api, engineStore())
        engine.onConnectionStateChanged(false)

        try {
            collection(api, cache, engine).get("d1")
            fail("expected an offline error")
        } catch (e: RiviumSyncException.NetworkException) {
            assertTrue(e.message!!.contains("Offline"))
        }
    }
}
