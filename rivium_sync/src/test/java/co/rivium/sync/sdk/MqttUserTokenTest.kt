package co.rivium.sync.sdk

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A project can require signed user tokens while the app has no signed-in
 * user yet. The realtime token request is then refused, and retrying cannot
 * help: the SDK used to report a connection failure and retry every 30 s
 * forever. It now waits, and connects when the app supplies a token.
 */
class MqttUserTokenTest {

    private var user: String? = null

    private fun manager(api: ApiClient) = MqttManager(
        context = mock<Context>(),
        config = RiviumSyncConfig.Builder("rv_live_test").autoReconnect(true).build(),
        apiClient = api,
        currentUser = { user },
    )

    private fun tokenRequiredApi(): ApiClient = mock<ApiClient>().also {
        // thenAnswer: Mockito refuses to stub a checked exception with thenThrow.
        whenever(it.fetchMqttToken()).thenAnswer {
            throw RiviumSyncException.UserTokenRequiredException("This project requires a signed user token")
        }
    }

    @Test
    fun `waits for a user token instead of retrying or failing`() {
        val api = tokenRequiredApi()
        val listener = mock<MqttManager.ConnectionListener>()
        val manager = manager(api).also { it.setConnectionListener(listener) }

        manager.connect(onSuccess = {}, onError = { throw AssertionError("connect must not fail") })

        Thread.sleep(2_500) // past when a retry would have fired
        verify(api, times(1)).fetchMqttToken()
        assertTrue(manager.awaitingUserToken)
        verify(listener, times(1)).onAwaitingUserToken()
        verify(listener, never()).onConnectionFailed(org.mockito.kotlin.any())
    }

    @Test
    fun `connects once the app has a token`() {
        val api = tokenRequiredApi()
        val manager = manager(api)
        manager.connect(onSuccess = {}, onError = {})
        Thread.sleep(300)
        verify(api, times(1)).fetchMqttToken()

        user = "user-1"
        manager.onUserMayHaveChanged()

        Thread.sleep(300)
        verify(api, times(2)).fetchMqttToken()
    }

    @Test
    fun `keeps waiting while there is still no user`() {
        val api = tokenRequiredApi()
        val manager = manager(api)
        manager.connect(onSuccess = {}, onError = {})
        Thread.sleep(300)

        manager.onUserMayHaveChanged() // e.g. the provider still returns null

        Thread.sleep(300)
        verify(api, times(1)).fetchMqttToken()
        assertTrue(manager.awaitingUserToken)
    }

    @Test
    fun `disconnect stops waiting`() {
        val api = tokenRequiredApi()
        val manager = manager(api)
        manager.connect(onSuccess = {}, onError = {})
        Thread.sleep(300)

        manager.disconnect()
        assertFalse(manager.awaitingUserToken)

        user = "user-1"
        manager.onUserMayHaveChanged()
        Thread.sleep(300)
        verify(api, times(1)).fetchMqttToken()
    }

    @Test
    fun `a token set before connect is asked for does not connect`() {
        val api = tokenRequiredApi()
        user = "user-1"
        manager(api).onUserMayHaveChanged()

        Thread.sleep(300)
        verify(api, never()).fetchMqttToken()
    }

    @Test
    fun `a listener added before the project is known moves to the real topic`() {
        val api = mock<ApiClient>().also {
            whenever(it.fetchMqttToken()).thenReturn(
                MqttTokenResponse(token = "jwt", expiresIn = null, projectId = "p1", mqtt = null)
            )
        }
        val manager = manager(api)

        // Before any connect the project id is unknown, as it is while the SDK
        // waits for a user token.
        val handle = manager.subscribe(manager.collectionTopic("db", "todos")) {}
        assertEquals(setOf("rivium_sync/unknown/db/todos/changes"), manager.subscribedTopics())

        manager.connect(onSuccess = {}, onError = {})
        Thread.sleep(300)
        assertEquals(setOf("rivium_sync/p1/db/todos/changes"), manager.subscribedTopics())

        // The handle still names the placeholder topic; removing it must work.
        manager.unsubscribe(handle)
        assertTrue(manager.subscribedTopics().isEmpty())
        manager.disconnect()
    }
}
