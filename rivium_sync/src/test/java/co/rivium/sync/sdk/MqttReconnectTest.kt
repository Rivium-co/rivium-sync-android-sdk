package co.rivium.sync.sdk

import android.content.Context
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * When the app starts offline the token request fails before any MQTT socket
 * exists, so the socket's own autoReconnect never runs. The SDK used to stay
 * "connecting" forever after the network came back; it now retries the whole
 * connect with backoff - and must stop the moment the app disconnects.
 */
class MqttReconnectTest {

    private fun manager(api: ApiClient) = MqttManager(
        context = mock<Context>(),
        config = RiviumSyncConfig.Builder("rv_live_test").autoReconnect(true).build(),
        apiClient = api,
    )

    private fun offlineApi(): ApiClient = mock<ApiClient>().also {
        whenever(it.fetchMqttToken()).thenThrow(RuntimeException("Unable to resolve host"))
    }

    @Test
    fun `retries the connect after the token request fails`() {
        val api = offlineApi()
        manager(api).connect(onSuccess = {}, onError = {})

        Thread.sleep(300)
        verify(api, times(1)).fetchMqttToken()

        Thread.sleep(2_500) // first retry is due after 2s
        verify(api, times(2)).fetchMqttToken()
    }

    @Test
    fun `disconnect cancels a pending retry`() {
        val api = offlineApi()
        val manager = manager(api)
        manager.connect(onSuccess = {}, onError = {})

        Thread.sleep(300)
        verify(api, times(1)).fetchMqttToken()

        manager.disconnect()
        Thread.sleep(2_500) // past when the retry would have fired
        verify(api, times(1)).fetchMqttToken()
    }
}
