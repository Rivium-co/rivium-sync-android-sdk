package co.rivium.sync.sdk

import co.rivium.sync.sdk.offline.ConflictResolver
import co.rivium.sync.sdk.offline.ConflictStrategy

/**
 * Configuration for RiviumSync SDK
 *
 * @param apiKey Your RiviumSync API key from the Rivium Console (rv_live_xxx or rv_test_xxx)
 */
data class RiviumSyncConfig private constructor(
    val apiKey: String,
    /** Optional user/device identifier for Security Rules (used as auth.uid) */
    val userId: String?,
    internal val apiUrl: String,
    internal val syncHost: String,
    internal val syncPort: Int,
    internal val useTls: Boolean,
    internal val useWebSocket: Boolean,
    internal val wsPath: String,
    val debugMode: Boolean,
    val autoReconnect: Boolean,
    internal val reconnectInterval: Long,
    internal val connectionTimeout: Int,
    internal val keepAliveInterval: Int,
    // Offline persistence options
    val offlineEnabled: Boolean,
    val offlineCacheSizeMb: Int,
    val syncOnReconnect: Boolean,
    val conflictStrategy: ConflictStrategy,
    val conflictResolver: ConflictResolver?,
    val maxSyncRetries: Int
) {
    companion object {
        // Internal configuration - not exposed to SDK users
        internal const val SYNC_API_URL = "https://sync.rivium.co"
        // TCP connection (DNS-only mode in Cloudflare for mqtt-sync subdomain)
        // Uses pn-protocol which wraps Paho MQTT - TCP works better on Android
        internal const val SYNC_HOST = "mqtt-sync.rivium.co"
        internal const val SYNC_PORT = 1884
        internal const val SYNC_USE_TLS = false
        internal const val SYNC_USE_WEBSOCKET = false
        internal const val SYNC_WS_PATH = "/mqtt"
        private const val DEFAULT_RECONNECT_INTERVAL = 5000L
        private const val DEFAULT_CONNECTION_TIMEOUT = 30
        private const val DEFAULT_KEEP_ALIVE = 60

        // Offline defaults
        private const val DEFAULT_OFFLINE_CACHE_SIZE_MB = 100
        private const val DEFAULT_MAX_SYNC_RETRIES = 3

        /**
         * Create a new RiviumSync configuration
         *
         * @param apiKey Your API key from Rivium Console (rv_live_xxx or rv_test_xxx)
         */
        fun builder(apiKey: String): Builder = Builder(apiKey)
    }

    class Builder(private val apiKey: String) {
        private var userId: String? = null
        private var debugMode: Boolean = false
        private var autoReconnect: Boolean = true
        private var offlineEnabled: Boolean = false
        private var offlineCacheSizeMb: Int = DEFAULT_OFFLINE_CACHE_SIZE_MB
        private var syncOnReconnect: Boolean = true
        private var conflictStrategy: ConflictStrategy = ConflictStrategy.SERVER_WINS
        private var conflictResolver: ConflictResolver? = null
        private var maxSyncRetries: Int = DEFAULT_MAX_SYNC_RETRIES

        /**
         * Set user/device identifier for Security Rules (used as auth.uid).
         * If not set, the SDK auto-generates a stable device ID.
         */
        fun userId(userId: String) = apply { this.userId = userId }

        /**
         * Enable debug logging
         */
        fun debugMode(debug: Boolean) = apply { this.debugMode = debug }

        /**
         * Enable automatic reconnection on disconnect
         */
        fun autoReconnect(autoReconnect: Boolean) = apply { this.autoReconnect = autoReconnect }

        /**
         * Enable offline persistence
         * When enabled, data is cached locally and operations work offline
         */
        fun offlineEnabled(enabled: Boolean) = apply { this.offlineEnabled = enabled }

        /**
         * Set the maximum cache size in megabytes
         * Default is 100MB. Old documents are evicted when limit is reached.
         */
        fun offlineCacheSizeMb(sizeMb: Int) = apply {
            require(sizeMb > 0) { "Cache size must be positive" }
            this.offlineCacheSizeMb = sizeMb
        }

        /**
         * Automatically sync pending operations when connection is restored
         * Default is true
         */
        fun syncOnReconnect(sync: Boolean) = apply { this.syncOnReconnect = sync }

        /**
         * Set the conflict resolution strategy for offline sync
         * Default is SERVER_WINS
         */
        fun conflictStrategy(strategy: ConflictStrategy) = apply { this.conflictStrategy = strategy }

        /**
         * Set a custom conflict resolver for MANUAL conflict strategy
         * Only used when conflictStrategy is MANUAL
         */
        fun conflictResolver(resolver: ConflictResolver) = apply { this.conflictResolver = resolver }

        /**
         * Set maximum number of sync retries for failed operations
         * Default is 3
         */
        fun maxSyncRetries(retries: Int) = apply {
            require(retries >= 0) { "Max retries cannot be negative" }
            this.maxSyncRetries = retries
        }

        fun build(): RiviumSyncConfig {
            require(apiKey.isNotBlank()) { "API key cannot be empty" }
            require(apiKey.startsWith("rv_live_") || apiKey.startsWith("rv_test_") || apiKey.startsWith("nl_live_") || apiKey.startsWith("nl_test_")) {
                "Invalid API key format. Must start with 'rv_live_' or 'rv_test_'"
            }
            return RiviumSyncConfig(
                apiKey = apiKey,
                userId = userId,
                apiUrl = SYNC_API_URL,
                syncHost = SYNC_HOST,
                syncPort = SYNC_PORT,
                useTls = SYNC_USE_TLS,
                useWebSocket = SYNC_USE_WEBSOCKET,
                wsPath = SYNC_WS_PATH,
                debugMode = debugMode,
                autoReconnect = autoReconnect,
                reconnectInterval = DEFAULT_RECONNECT_INTERVAL,
                connectionTimeout = DEFAULT_CONNECTION_TIMEOUT,
                keepAliveInterval = DEFAULT_KEEP_ALIVE,
                offlineEnabled = offlineEnabled,
                offlineCacheSizeMb = offlineCacheSizeMb,
                syncOnReconnect = syncOnReconnect,
                conflictStrategy = conflictStrategy,
                conflictResolver = conflictResolver,
                maxSyncRetries = maxSyncRetries
            )
        }
    }

    internal fun getSyncServerUri(): String {
        return if (useWebSocket) {
            val protocol = if (useTls) "wss" else "ws"
            "$protocol://$syncHost:$syncPort$wsPath"
        } else {
            val protocol = if (useTls) "ssl" else "tcp"
            "$protocol://$syncHost:$syncPort"
        }
    }

}
