package co.rivium.sync.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import co.rivium.sync.sdk.offline.LocalStorageManager
import co.rivium.sync.sdk.offline.SyncEngine
import co.rivium.sync.sdk.offline.SyncState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * RiviumSync - Realtime Database SDK
 *
 * A Firebase-like realtime database service for instant data synchronization
 * across all connected devices.
 *
 */
class RiviumSync(
    context: Context,
    private val config: RiviumSyncConfig
) {
    internal val apiClient: ApiClient
    private val mqttManager: MqttManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Offline support
    internal val localStorageManager: LocalStorageManager?
    internal val syncEngine: SyncEngine?

    private var isConnected = false
    private var connectionListener: ConnectionListener? = null

    /** Resolved userId for Security Rules (auth.uid) */
    val userId: String

    /**
     * The signed user token the SDK sends.
     *
     * Call `sync.userTokens.set(token)` after your app signs a user in or
     * refreshes their token; pass a provider in the config to let the SDK
     * refresh by itself.
     */
    val userTokens: UserTokenStore
        get() = apiClient.userTokens

    init {
        RiviumSyncLogger.debugMode = config.debugMode
        userId = getOrCreateUserId(context)
        apiClient = ApiClient(config, userId)
        config.userToken?.let { apiClient.userTokens.set(it) }
        config.userTokenProvider?.let { apiClient.userTokens.provider = it }
        mqttManager = MqttManager(context.applicationContext, config, apiClient)
        apiClient.userTokens.onUserMayHaveChanged = { mqttManager.onUserMayHaveChanged() }

        // Initialize offline components if enabled
        if (config.offlineEnabled) {
            localStorageManager = LocalStorageManager(context.applicationContext)
            syncEngine = SyncEngine(
                apiClient = apiClient,
                localStore = localStorageManager,
                conflictStrategy = config.conflictStrategy,
                conflictResolver = config.conflictResolver,
                maxRetries = config.maxSyncRetries
            )
            RiviumSyncLogger.i("RiviumSync: Offline persistence enabled")
        } else {
            localStorageManager = null
            syncEngine = null
        }

        mqttManager.setConnectionListener(object : MqttManager.ConnectionListener {
            override fun onConnected() {
                isConnected = true
                // Notify sync engine about connection state
                syncEngine?.onConnectionStateChanged(true)
                connectionListener?.onConnected()
            }

            override fun onDisconnected(cause: Throwable?) {
                isConnected = false
                // Notify sync engine about connection state
                syncEngine?.onConnectionStateChanged(false)
                connectionListener?.onDisconnected(cause)
            }

            override fun onConnectionFailed(cause: Throwable) {
                // A failed connect means we are offline for now; the MqttManager
                // keeps retrying and onConnected flips this back.
                syncEngine?.onConnectionStateChanged(false)
                connectionListener?.onConnectionFailed(cause)
            }

            override fun onAwaitingUserToken() {
                // Offline for now; onConnected flips this back once the app
                // has set a token.
                syncEngine?.onConnectionStateChanged(false)
                onAwaitingUserToken?.invoke()
            }
        })

        RiviumSyncLogger.i("RiviumSync SDK initialized")
    }

    /**
     * True while [connect] is waiting for a user token: the project requires
     * signed user tokens and the app has not supplied one (no one is signed in
     * yet). The SDK connects by itself once `userTokens.set(token)` is called,
     * or after `userTokens.refresh()` when a provider is used.
     */
    val isAwaitingUserToken: Boolean
        get() = mqttManager.awaitingUserToken

    /** Called when [connect] starts waiting for a user token. */
    @Volatile
    var onAwaitingUserToken: (() -> Unit)? = null

    /**
     * Connection state listener
     */
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected(cause: Throwable?)
        fun onConnectionFailed(cause: Throwable)
    }

    /**
     * Set connection state listener
     */
    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }

    /**
     * Connect to realtime sync service
     * Must be called before listening to changes
     */
    suspend fun connect() = suspendCoroutine { continuation ->
        mqttManager.connect(
            onSuccess = { continuation.resume(Unit) },
            onError = { error ->
                continuation.resumeWithException(
                    RiviumSyncException.NetworkException("Failed to connect: ${error.message}", error)
                )
            }
        )
    }

    /**
     * Connect with callback
     */
    fun connect(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        mqttManager.connect(onSuccess, onError)
    }

    /**
     * Disconnect from realtime sync service
     */
    fun disconnect() {
        mqttManager.disconnect()
        isConnected = false
        syncEngine?.onConnectionStateChanged(false)
        connectionListener?.onDisconnected(null)
        RiviumSyncLogger.i("RiviumSync disconnected")
    }

    /**
     * Check if connected to realtime service
     */
    fun isConnected(): Boolean = mqttManager.isConnected()

    /**
     * Get a database reference by its NAME.
     *
     * ```kotlin
     * val todos = riviumSync.database("my-app").collection("todos")
     * ```
     *
     * @param databaseId The database NAME exactly as shown in Rivium Console
     * (for example `"my-app"`), not its UUID. Realtime updates are published per
     * database name, so listeners only receive changes when the database is
     * addressed by name; a UUID still works for plain reads and writes but live
     * updates never arrive. (The parameter keeps its historical name for
     * source compatibility with named arguments.)
     */
    fun database(databaseId: String): SyncDatabase {
        return SyncDatabaseImpl(databaseId, "", apiClient, mqttManager, localStorageManager, syncEngine)
    }

    // ==================== Batch Operations ====================

    /**
     * Create a new WriteBatch for atomic operations.
     *
     * A WriteBatch is used to perform multiple writes as a single atomic unit.
     * None of the writes will be committed until `commit()` is called.
     *
     * Usage:
     * ```kotlin
     * val batch = rivium_sync.batch()
     * batch.set(usersCollection, "user1", mapOf("name" to "John"))
     * batch.update(ordersCollection, "order1", mapOf("status" to "shipped"))
     * batch.delete(tempCollection, "temp1")
     * batch.commit()
     * ```
     *
     * @return A new WriteBatch instance
     */
    fun batch(): WriteBatch {
        return WriteBatch(apiClient)
    }

    // ==================== Offline API ====================

    /**
     * Check if offline persistence is enabled
     */
    fun isOfflineEnabled(): Boolean = config.offlineEnabled

    /**
     * Get the current sync state (IDLE, SYNCING, OFFLINE, ERROR)
     * Only available when offline persistence is enabled
     */
    fun getSyncState(): StateFlow<SyncState>? = syncEngine?.syncState

    /**
     * Get the count of pending operations waiting to be synced
     * Only available when offline persistence is enabled
     */
    fun getPendingCount(): StateFlow<Int>? = syncEngine?.pendingCount

    /**
     * Force sync all pending operations now
     * Only available when offline persistence is enabled
     */
    fun forceSyncNow() {
        syncEngine?.forceSync()
    }

    /**
     * Add a sync listener to monitor sync events
     * Only available when offline persistence is enabled
     */
    fun addSyncListener(listener: SyncListener) {
        syncEngine?.addSyncListener(listener)
    }

    /**
     * Remove a sync listener
     */
    fun removeSyncListener(listener: SyncListener) {
        syncEngine?.removeSyncListener(listener)
    }

    /**
     * Clear all cached data
     * Only available when offline persistence is enabled
     */
    suspend fun clearOfflineCache() {
        localStorageManager?.clearAll()
    }

    /**
     * List all databases for the current user
     */
    suspend fun listDatabases(): List<DatabaseInfo> {
        return apiClient.listDatabases()
    }


    /**
     * Release resources
     * Call this when you're done with RiviumSync
     */
    fun destroy() {
        disconnect()
        syncEngine?.destroy()
        coroutineScope.cancel()
        RiviumSyncLogger.i("RiviumSync destroyed")
    }

    @SuppressLint("HardwareIds")
    private fun getOrCreateUserId(context: Context): String {
        // If developer provided a userId, use it
        config.userId?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_USER_ID, null)

        if (id == null) {
            id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, id).apply()
            RiviumSyncLogger.d("Generated new userId: $id")
        }

        return id
    }

    companion object {
        private const val PREFS_NAME = "rivium_sync_prefs"
        private const val KEY_USER_ID = "user_id"
        private var instance: RiviumSync? = null

        /**
         * Get the singleton instance
         */
        fun getInstance(): RiviumSync {
            return instance ?: throw RiviumSyncException.NotInitializedException()
        }

        /**
         * Initialize the singleton instance
         */
        fun initialize(context: Context, config: RiviumSyncConfig): RiviumSync {
            return RiviumSync(context, config).also { instance = it }
        }

        /**
         * Check if SDK is initialized
         */
        fun isInitialized(): Boolean = instance != null

        /**
         * SDK version (the published Maven version), also sent to the backend
         * in the `X-Rivium-SDK` header.
         */
        const val SDK_VERSION: String = BuildConfig.SDK_VERSION

        /**
         * SDK version. Same value as [SDK_VERSION] (it used to say "1.0.0").
         */
        const val VERSION: String = BuildConfig.SDK_VERSION
    }
}

/**
 * Internal database implementation
 */
internal class SyncDatabaseImpl(
    override val id: String,
    override val name: String,
    private val apiClient: ApiClient,
    private val mqttManager: MqttManager,
    private val localStorageManager: LocalStorageManager?,
    private val syncEngine: SyncEngine?
) : SyncDatabase {

    override fun collection(collectionIdOrName: String): SyncCollection {
        return SyncCollectionImpl(
            id = collectionIdOrName,
            name = collectionIdOrName,
            databaseId = id,
            apiClient = apiClient,
            mqttManager = mqttManager,
            localStorageManager = localStorageManager,
            syncEngine = syncEngine
        )
    }

    override suspend fun listCollections(): List<CollectionInfo> {
        return apiClient.listCollections(id)
    }

    override suspend fun createCollection(name: String): SyncCollection {
        val info = apiClient.createCollection(id, name)
        // Keyed by name, like collection(): realtime topics carry names, so a
        // UUID-keyed collection would never receive live updates.
        return SyncCollectionImpl(
            id = info.name,
            name = info.name,
            databaseId = id,
            apiClient = apiClient,
            mqttManager = mqttManager,
            localStorageManager = localStorageManager,
            syncEngine = syncEngine
        )
    }

    override suspend fun deleteCollection(collectionId: String) {
        apiClient.deleteCollection(collectionId)
    }
}
