package co.rivium.sync.sdk

import co.rivium.sync.sdk.internal.MqttEndpoint

import android.content.Context
import android.os.Handler
import android.os.Looper
import co.rivium.protocol.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * MQTT Manager for realtime data synchronization
 * Uses pn-protocol (Pushino) as the transport layer
 */
internal class MqttManager(
    private val context: Context,
    private val config: RiviumSyncConfig,
    private val apiClient: ApiClient,
    /** Who the user token is for right now; null when there is no token. */
    private val currentUser: () -> String? = { apiClient.userTokens.currentUser() }
) {
    private var connectionListener: ConnectionListener? = null

    /** Project the API key belongs to, learned from POST /connections/token. */
    private var projectId: String? = null
    // Track subscriptions locally so we can subscribe before connection and bridge PNMessage -> String callback
    private val subscriptions = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()
    private val clientId: String = "rivium_sync_${UUID.randomUUID().toString().take(8)}"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadScheduledExecutor()

    // Track which topics we've already called stream() for on the current PNSocket.
    // PNSocket keeps its own messageListeners and activeChannels across reconnects,
    // so we only need to call stream() once per topic per PNSocket instance.
    private val pnListeners = ConcurrentHashMap<String, PNMessageListener>()

    // Track whether we have subscribed at least once on the current PNSocket.
    // On reconnect, PNSocket.resubscribeChannels() handles MQTT re-subscription
    // automatically — we do NOT need to call stream() again.
    private var hasSubscribedOnce = false

    // Hold a reference to the PNSocket we added listeners to
    private var socket: PNSocket? = null

    // The socket's own autoReconnect only covers a socket that exists. If the
    // token request fails (e.g. the app started offline) there is no socket yet,
    // so without this the SDK stayed "connecting" forever after the network
    // came back. These drive a retry of the whole connect, token included.
    @Volatile private var wantConnected = false
    @Volatile private var tokenRetryAttempt = 0
    private var tokenRetry: ScheduledFuture<*>? = null

    // The project requires a signed user token and the app has none yet (no
    // one is signed in). That is not a failure and retrying cannot fix it: the
    // connect is parked here until the app supplies a token.
    @Volatile var awaitingUserToken = false
        private set

    // The user the open socket was authorised as, to notice a different one.
    @Volatile private var socketUser: String? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected(cause: Throwable?)
        fun onConnectionFailed(cause: Throwable)
        /** Connect is parked until the app supplies a user token. */
        fun onAwaitingUserToken() {}
    }

    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }

    fun connect(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        RiviumSyncLogger.i("MqttManager.connect() called, socket=${socket != null}, isConnected=${isConnected()}")
        wantConnected = true
        awaitingUserToken = false
        cancelTokenRetry()
        tokenRetryAttempt = 0
        connectInternal(onSuccess, onError)
    }

    /**
     * The app set, cleared or refreshed its user token. Connect if that is what
     * the SDK was waiting for; reconnect if the socket belongs to another user.
     * A new token for the same user changes nothing here.
     */
    fun onUserMayHaveChanged() {
        if (!wantConnected) return
        executor.execute {
            if (!wantConnected) return@execute
            val user = try { currentUser() } catch (e: Exception) { null }
            if (awaitingUserToken) {
                if (user == null) return@execute
                RiviumSyncLogger.i("User token available; connecting")
                awaitingUserToken = false
                connectInternal({}, {})
            } else if (socket != null && user != socketUser) {
                RiviumSyncLogger.i("Signed-in user changed; reconnecting")
                cancelTokenRetry()
                tokenRetryAttempt = 0
                // Keep `subscriptions`: they are subscribed again on connect.
                try { socket?.close() } catch (e: Exception) { RiviumSyncLogger.e("Error closing socket", e) }
                socket = null
                hasSubscribedOnce = false
                pnListeners.clear()
                connectInternal({}, {})
            }
        }
    }

    private fun connectInternal(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {

        if (socket?.isConnected() == true) {
            RiviumSyncLogger.d("Already connected")
            onSuccess()
            return
        }

        // Token fetch must be async (network call), but socket setup follows RiviumPush pattern:
        // create socket → add listeners → open() → let onConnected callback handle the rest
        executor.execute {
            try {
                RiviumSyncLogger.d("Fetching MQTT token...")
                val tokenResponse = apiClient.fetchMqttToken()
                socketUser = try { currentUser() } catch (e: Exception) { null }
                tokenRetryAttempt = 0
                projectId = tokenResponse.projectId
                adoptProjectTopics()
                RiviumSyncLogger.d("MQTT token obtained, token length=${tokenResponse.token.length}")

                // Close any existing socket to prevent orphaned MQTT connections
                if (socket != null) {
                    RiviumSyncLogger.d("Closing existing socket before reconnect")
                    socket?.close()
                    socket = null
                    hasSubscribedOnce = false
                    pnListeners.clear()
                }

                // The server says where to connect (it can move clients to an encrypted port); only a valid
                // rivium.co endpoint is used, else the built-in one. There is no fallback from TLS to plain.
                val endpoint = MqttEndpoint.resolve(
                    tokenResponse.mqtt?.host,
                    tokenResponse.mqtt?.port,
                    tokenResponse.mqtt?.useTls,
                    MqttEndpoint(config.syncHost, config.syncPort, config.useTls),
                )

                val pnConfig = PNConfig.builder()
                    .gateway(endpoint.host)
                    .port(endpoint.port)
                    .clientId(clientId)
                    .auth(PNAuth.basic("jwt", tokenResponse.token))
                    .heartbeatInterval(config.keepAliveInterval)
                    .connectionTimeout(config.connectionTimeout)
                    .freshStart(true)
                    .autoReconnect(config.autoReconnect)
                    .secure(endpoint.useTls)
                    .build()

                RiviumSyncLogger.i("PNConfig: gateway=${endpoint.host}, port=${endpoint.port}, clientId=$clientId, secure=${endpoint.useTls}, autoReconnect=${config.autoReconnect}")

                socket = PNSocket(pnConfig)

                // Add connection listener (same pattern as RiviumPush PNSocketManager)
                socket!!.addConnectionListener(object : PNConnectionListener {
                    override fun onStateChanged(state: PNState) {
                        RiviumSyncLogger.d("PN state changed: $state")
                    }

                    override fun onConnected() {
                        RiviumSyncLogger.i("PN connected, hasSubscribedOnce=$hasSubscribedOnce")
                        if (!hasSubscribedOnce) {
                            subscribePendingTopics()
                            hasSubscribedOnce = true
                        } else {
                            RiviumSyncLogger.d("Reconnected - PNSocket handles resubscription automatically")
                        }
                        connectionListener?.onConnected()
                    }

                    override fun onDisconnected(reason: String?) {
                        RiviumSyncLogger.w("PN disconnected: $reason")
                        connectionListener?.onDisconnected(reason?.let { Exception(it) })
                    }

                    override fun onReconnecting(attempt: Int, nextRetryMs: Long) {
                        RiviumSyncLogger.i("PN reconnecting: attempt=$attempt, nextRetry=${nextRetryMs}ms")
                    }
                })

                // Add error listener
                socket!!.addErrorListener { error ->
                    RiviumSyncLogger.e("PN error: code=${error.code}, message=${error.message}")
                    if (error.code == PNError.Code.CONNECTION_FAILED) {
                        connectionListener?.onConnectionFailed(
                            error.cause ?: Exception(error.message)
                        )
                    }
                }

                // Open connection — onConnected callback fires when ready, no polling needed
                socket!!.open()
                mainHandler.post { onSuccess() }

            } catch (e: RiviumSyncException.UserTokenRequiredException) {
                // Nobody is signed in yet. Wait for the app's token instead of
                // retrying; connect() itself has not failed.
                RiviumSyncLogger.i("This project requires a user token; realtime connects when one is set")
                awaitingUserToken = true
                tokenRetryAttempt = 0
                connectionListener?.onAwaitingUserToken()
                mainHandler.post { onSuccess() }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to initialize PN Protocol", e)
                val firstFailure = tokenRetryAttempt == 0
                if (firstFailure) {
                    // Tell the app once per failure streak, not on every retry.
                    mainHandler.post { onError(e) }
                    connectionListener?.onConnectionFailed(e)
                }
                scheduleTokenRetry(onSuccess, onError)
            }
        }
    }

    private fun scheduleTokenRetry(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        if (!config.autoReconnect || !wantConnected) return
        val delayMs = (TOKEN_RETRY_BASE_MS shl tokenRetryAttempt.coerceAtMost(4))
            .coerceAtMost(TOKEN_RETRY_MAX_MS)
        tokenRetryAttempt++
        RiviumSyncLogger.i("Connect failed; retrying in ${delayMs}ms (attempt $tokenRetryAttempt)")
        tokenRetry = executor.schedule({
            // disconnect() may have run while we waited.
            if (wantConnected) connectInternal(onSuccess, onError)
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun cancelTokenRetry() {
        tokenRetry?.cancel(false)
        tokenRetry = null
    }

    fun disconnect() {
        RiviumSyncLogger.i("MqttManager.disconnect() called, subscriptions=${subscriptions.keys}, pnListeners=${pnListeners.keys}")
        // A pending retry must not bring the connection back after the app
        // asked for it to close.
        wantConnected = false
        awaitingUserToken = false
        cancelTokenRetry()
        try {
            subscriptions.clear()
            pnListeners.clear()
            hasSubscribedOnce = false
            socket?.close()
            socket = null
            RiviumSyncLogger.i("PN Protocol disconnected")
        } catch (e: Exception) {
            RiviumSyncLogger.e("Error disconnecting PN Protocol", e)
        }
    }

    fun isConnected(): Boolean {
        return try {
            socket?.isConnected() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Subscribe to a topic for realtime updates
     */
    fun subscribe(topic: String, callback: (String) -> Unit): SubscriptionHandle {
        // Add to local subscriptions
        subscriptions.getOrPut(topic) { CopyOnWriteArrayList() }.add(callback)
        val callbackCount = subscriptions[topic]?.size ?: 0
        RiviumSyncLogger.i("MqttManager.subscribe: topic=$topic, isConnected=${isConnected()}, callbackCount=$callbackCount, allTopics=${subscriptions.keys}, pnListeners=${pnListeners.keys}")

        // Subscribe via pn-protocol if connected
        if (isConnected()) {
            subscribeViaPNProtocol(topic)
        } else {
            RiviumSyncLogger.w("MqttManager.subscribe: Not connected, topic $topic will be subscribed on connect")
        }

        return SubscriptionHandle(topic, callback)
    }

    /**
     * Unsubscribe from a topic
     */
    fun unsubscribe(handle: SubscriptionHandle) {
        // A handle made before the project id was known still names the
        // placeholder topic; its callbacks have moved to the real one.
        val topic = if (subscriptions.containsKey(handle.topic)) handle.topic else projectTopic(handle.topic)
        val callbacks = subscriptions[topic]
        callbacks?.remove(handle.callback)
        RiviumSyncLogger.i("MqttManager.unsubscribe: topic=$topic, remainingCallbacks=${callbacks?.size ?: 0}")

        // Detach from pn-protocol if no more callbacks for this topic
        if (callbacks?.isEmpty() == true) {
            subscriptions.remove(topic)
            pnListeners.remove(topic)
            try {
                if (isConnected()) {
                    socket?.detach(topic)
                    RiviumSyncLogger.d("Detached from channel: $topic")
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to detach from channel: $topic", e)
            }
        }
    }

    /** [topic] under the real project id, if it was built before that was known. */
    private fun projectTopic(topic: String): String {
        val id = projectId ?: return topic
        return if (topic.startsWith(UNKNOWN_PROJECT_PREFIX)) "rivium_sync/$id/" + topic.removePrefix(UNKNOWN_PROJECT_PREFIX) else topic
    }

    /**
     * A listener added before the first realtime token answer (the app was
     * offline, or waiting for a user token) was keyed to a placeholder project.
     * Nothing is published there: move it to the real topic before subscribing.
     */
    private fun adoptProjectTopics() {
        subscriptions.keys.filter { it.startsWith(UNKNOWN_PROJECT_PREFIX) }.forEach { placeholder ->
            val callbacks = subscriptions.remove(placeholder) ?: return@forEach
            subscriptions.getOrPut(projectTopic(placeholder)) { CopyOnWriteArrayList() }.addAll(callbacks)
            pnListeners.remove(placeholder)
        }
    }

    /** Topics with at least one listener; for tests. */
    internal fun subscribedTopics(): Set<String> = subscriptions.keys.toSet()

    /**
     * Publish a message to a topic
     */
    fun publish(topic: String, payload: String, qos: Int = 1, retained: Boolean = false) {
        if (!isConnected()) {
            RiviumSyncLogger.w("Cannot publish, not connected")
            return
        }

        try {
            val message = PNMessage.builder()
                .channel(topic)
                .payload(payload)
                .mode(PNDeliveryMode.fromQos(qos))
                .persist(retained)
                .build()
            socket?.dispatch(message)
            RiviumSyncLogger.d("Published to $topic (${payload.length} bytes)")
        } catch (e: Exception) {
            RiviumSyncLogger.e("Failed to publish to $topic", e)
        }
    }

    /**
     * Subscribe to a topic via pn-protocol, bridging PNMessage to String callback.
     * Only calls stream() once per topic per PNSocket instance — PNSocket handles
     * MQTT resubscription on reconnect automatically via its activeChannels set.
     */
    private fun subscribeViaPNProtocol(topic: String) {
        if (pnListeners.containsKey(topic)) return

        RiviumSyncLogger.d("Subscribing to channel: $topic")

        val listener = PNMessageListener { message ->
            val payload = message.payloadAsString()
            RiviumSyncLogger.d("Message received on ${message.channel}")
            subscriptions[message.channel]?.forEach { callback ->
                try {
                    callback(payload)
                } catch (e: Exception) {
                    RiviumSyncLogger.e("Error in subscription callback for ${message.channel}", e)
                }
            }
        }

        pnListeners[topic] = listener

        try {
            socket?.stream(topic, PNDeliveryMode.RELIABLE, listener)
        } catch (e: Exception) {
            RiviumSyncLogger.e("Failed to stream channel: $topic", e)
            pnListeners.remove(topic)
        }
    }

    /**
     * Subscribe all pending topics (topics registered before connection was established).
     * Called only once on first connect. On reconnect, PNSocket handles resubscription automatically.
     */
    private fun subscribePendingTopics() {
        val topics = subscriptions.keys.toList()
        RiviumSyncLogger.i("subscribePendingTopics: ${topics.size} pending topics: $topics")
        topics.forEach { topic ->
            subscribeViaPNProtocol(topic)
        }
    }

    /**
     * Topic for collection changes.
     *
     * `rivium_sync/{projectId}/{databaseName}/{collectionName}/changes` - the
     * names the app uses, under the project id from POST /connections/token, so
     * the same database name in two projects cannot collide. The broker grants
     * a client only its own `rivium_sync/{projectId}/#`.
     */
    fun collectionTopic(databaseId: String, collectionId: String): String {
        return "rivium_sync/${projectId ?: "unknown"}/$databaseId/$collectionId/changes"
    }

    /** Topic for one document's changes. */
    fun documentTopic(databaseId: String, collectionId: String, documentId: String): String {
        return "rivium_sync/${projectId ?: "unknown"}/$databaseId/$collectionId/$documentId"
    }

    inner class SubscriptionHandle(
        val topic: String,
        val callback: (String) -> Unit
    )

    companion object {
        private const val TOKEN_RETRY_BASE_MS = 2_000L
        private const val TOKEN_RETRY_MAX_MS = 30_000L
        private const val UNKNOWN_PROJECT_PREFIX = "rivium_sync/unknown/"
    }
}
