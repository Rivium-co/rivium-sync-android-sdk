package co.rivium.sync.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import co.rivium.protocol.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * MQTT Manager for realtime data synchronization
 * Uses pn-protocol (Pushino) as the transport layer
 */
internal class MqttManager(
    private val context: Context,
    private val config: RiviumSyncConfig,
    private val apiClient: ApiClient
) {
    private var connectionListener: ConnectionListener? = null
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

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected(cause: Throwable?)
        fun onConnectionFailed(cause: Throwable)
    }

    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }

    fun connect(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        RiviumSyncLogger.i("MqttManager.connect() called, socket=${socket != null}, isConnected=${isConnected()}")

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
                RiviumSyncLogger.d("MQTT token obtained, token length=${tokenResponse.token.length}")

                // Close any existing socket to prevent orphaned MQTT connections
                if (socket != null) {
                    RiviumSyncLogger.d("Closing existing socket before reconnect")
                    socket?.close()
                    socket = null
                    hasSubscribedOnce = false
                    pnListeners.clear()
                }

                val pnConfig = PNConfig.builder()
                    .gateway(config.syncHost)
                    .port(config.syncPort)
                    .clientId(clientId)
                    .auth(PNAuth.basic("jwt", tokenResponse.token))
                    .heartbeatInterval(config.keepAliveInterval)
                    .connectionTimeout(config.connectionTimeout)
                    .freshStart(true)
                    .autoReconnect(config.autoReconnect)
                    .secure(config.useTls)
                    .build()

                RiviumSyncLogger.i("PNConfig: gateway=${config.syncHost}, port=${config.syncPort}, clientId=$clientId, secure=${config.useTls}, autoReconnect=${config.autoReconnect}")

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

            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to initialize PN Protocol", e)
                mainHandler.post { onError(e) }
            }
        }
    }

    fun disconnect() {
        RiviumSyncLogger.i("MqttManager.disconnect() called, subscriptions=${subscriptions.keys}, pnListeners=${pnListeners.keys}")
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
        val callbacks = subscriptions[handle.topic]
        callbacks?.remove(handle.callback)
        RiviumSyncLogger.i("MqttManager.unsubscribe: topic=${handle.topic}, remainingCallbacks=${callbacks?.size ?: 0}")

        // Detach from pn-protocol if no more callbacks for this topic
        if (callbacks?.isEmpty() == true) {
            subscriptions.remove(handle.topic)
            pnListeners.remove(handle.topic)
            try {
                if (isConnected()) {
                    socket?.detach(handle.topic)
                    RiviumSyncLogger.d("Detached from channel: ${handle.topic}")
                }
            } catch (e: Exception) {
                RiviumSyncLogger.e("Failed to detach from channel: ${handle.topic}", e)
            }
        }
    }

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
            RiviumSyncLogger.d("Published to $topic: $payload")
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
     * Generate topic for collection changes
     */
    fun collectionTopic(databaseId: String, collectionId: String): String {
        return "rivium_sync/$databaseId/$collectionId/changes"
    }

    /**
     * Generate topic for document changes
     */
    fun documentTopic(databaseId: String, collectionId: String, documentId: String): String {
        return "rivium_sync/$databaseId/$collectionId/$documentId"
    }

    inner class SubscriptionHandle(
        val topic: String,
        val callback: (String) -> Unit
    )
}
