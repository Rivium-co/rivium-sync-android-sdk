package co.rivium.sync.sdk

import co.rivium.sync.sdk.internal.SdkIdentity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

/** X-Rivium-SDK: `android/<version>`, or the wrapper first with the native SDK underneath. */
@Suppress("DEPRECATION") // data-class copy() on a private constructor
class SdkHeaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() { server = MockWebServer().apply { start() } }

    @After
    fun tearDown() = server.shutdown()

    private fun api(builder: RiviumSyncConfig.Builder = RiviumSyncConfig.Builder("rv_live_test")) =
        ApiClient(builder.build().copy(apiUrl = server.url("/").toString().trimEnd('/')))

    @Test
    fun `SDK_VERSION is the published Maven version`() {
        val props = Properties().apply {
            File("../gradle.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        props.getProperty("VERSION_NAME")?.let { assertThat(RiviumSync.SDK_VERSION).isEqualTo(it) }
        assertThat(RiviumSync.VERSION).isEqualTo(RiviumSync.SDK_VERSION)
    }

    @Test
    fun `every request sends X-Rivium-SDK android-version`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setBody("""{"token":"t","expiresIn":60}"""))

        val api = api()
        api.listDatabases()
        runCatching { api.fetchMqttToken() }

        repeat(2) {
            val request = server.takeRequest()
            assertThat(request.getHeader("X-Rivium-SDK")).isEqualTo("android/${RiviumSync.SDK_VERSION}")
            assertThat(request.getHeader("X-API-Key")).isEqualTo("rv_live_test")
        }
    }

    @Test
    fun `a wrapper is reported first, with the native SDK underneath`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        api(RiviumSyncConfig.Builder("rv_live_test").wrapperSdk("flutter", "0.2.4")).listDatabases()

        assertThat(server.takeRequest().getHeader("X-Rivium-SDK"))
            .isEqualTo("flutter/0.2.4 android/${RiviumSync.SDK_VERSION}")
    }

    @Test
    fun `an invalid or half-set wrapper identity falls back to the native one`() {
        assertThat(SdkIdentity.resolve(null, null)).isEqualTo(SdkIdentity.native)
        assertThat(SdkIdentity.resolve("flutter", null)).isEqualTo(SdkIdentity.native)
        assertThat(SdkIdentity.resolve("flut ter", "1.0")).isEqualTo(SdkIdentity.native)
        assertThat(SdkIdentity.resolve("x".repeat(33), "1.0")).isEqualTo(SdkIdentity.native)
        assertThat(SdkIdentity.resolve("react-native", "1.0.0+2").headerValue)
            .isEqualTo("react-native/1.0.0+2 android/${RiviumSync.SDK_VERSION}")
    }
}
