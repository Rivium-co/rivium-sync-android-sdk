package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The server answers 100 documents at a time unless asked otherwise. getAll()
 * used to make a single request, so it stopped at the first 100 without
 * saying so - and the cache cleanup that relies on a full read would then have
 * deleted everything past that page.
 */
@Suppress("DEPRECATION") // data-class copy() on a private constructor
class CollectionPagingTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val config = RiviumSyncConfig.Builder("rv_live_test").build()
            .copy(apiUrl = server.url("/").toString().trimEnd('/'))
        api = ApiClient(config)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun page(from: Int, count: Int, total: Int?): MockResponse {
        val docs = (from until from + count).joinToString(",") { """{"id":"doc-$it","data":{}}""" }
        val totalField = total?.let { ""","total":$it""" } ?: ""
        return MockResponse().setBody("""{"data":[$docs]$totalField}""")
    }

    @Test
    fun `reads every page, not just the first 100`() = runTest {
        server.enqueue(page(0, 100, total = 150))
        server.enqueue(page(100, 50, total = 150))

        val snapshot = api.fetchCollection("db", "todos")

        assertThat(snapshot.documents).hasSize(150)
        assertThat(snapshot.documents.last().id).isEqualTo("doc-149")
        assertThat(snapshot.complete).isTrue()
        assertThat(server.takeRequest().path).endsWith("?skip=0&limit=100")
        assertThat(server.takeRequest().path).endsWith("?skip=100&limit=100")
    }

    @Test
    fun `a single short page is complete`() = runTest {
        server.enqueue(page(0, 10, total = 10))

        val snapshot = api.fetchCollection("db", "todos")

        assertThat(snapshot.documents).hasSize(10)
        assertThat(snapshot.complete).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `without a total it is never treated as complete`() = runTest {
        // An older API that sends no total: keep the documents, but the cache
        // must not use this to delete anything.
        server.enqueue(page(0, 10, total = null))

        val snapshot = api.fetchCollection("db", "todos")

        assertThat(snapshot.documents).hasSize(10)
        assertThat(snapshot.complete).isFalse()
    }

    @Test
    fun `a collection that changed while paging is not complete`() = runTest {
        // Two documents were deleted between the pages, so we saw fewer than
        // the total first reported: do not trust it for deleting.
        server.enqueue(page(0, 100, total = 150))
        server.enqueue(page(100, 48, total = 150))

        val snapshot = api.fetchCollection("db", "todos")

        assertThat(snapshot.documents).hasSize(148)
        assertThat(snapshot.complete).isFalse()
    }
}
