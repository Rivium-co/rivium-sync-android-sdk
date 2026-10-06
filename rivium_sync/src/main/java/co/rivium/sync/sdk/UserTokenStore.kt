package co.rivium.sync.sdk

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the signed user token the SDK sends as `X-User-Token`.
 *
 * Security Rules read `auth.uid`. The `X-User-Id` header cannot decide that -
 * the API key shipped in the app is public, so any client could claim any user.
 * Your backend mints a token instead (it holds the server secret) and the app
 * hands it to the SDK here.
 *
 * Supply a [provider] so the SDK can fetch a fresh token by itself when the one
 * it holds is close to expiry or the server rejects it as expired. Without a
 * provider, call [set] again when you refresh.
 */
class UserTokenStore internal constructor() {
    private val token = AtomicReference<String?>(null)
    private val expiresAtSeconds = AtomicReference(0L)

    /**
     * Fetches a token for the signed-in user, called on a background thread.
     * Return null to send no token at all.
     */
    @Volatile
    var provider: (() -> String?)? = null

    /** Told when the app changes who is signed in, so realtime can follow. */
    @Volatile
    internal var onUserMayHaveChanged: (() -> Unit)? = null

    /**
     * Replace the current token. Safe to call from any thread.
     *
     * If the SDK was waiting for a token to connect, it connects now; if it is
     * connected as a different user, it reconnects as this one.
     */
    fun set(newToken: String?) {
        store(newToken)
        onUserMayHaveChanged?.invoke()
    }

    /**
     * For apps that use a [provider]: call this when the user signs in or out.
     * The SDK asks the provider again and connects, or reconnects, as that user.
     */
    fun refresh() {
        invalidate()
        onUserMayHaveChanged?.invoke()
    }

    private fun store(newToken: String?) {
        token.set(newToken)
        expiresAtSeconds.set(newToken?.let { expiryOf(it) } ?: 0L)
    }

    /** Forget the current token so the next request asks [provider] for another. */
    fun invalidate() {
        token.set(null)
        expiresAtSeconds.set(0L)
    }

    /**
     * The token to send, refreshing through [provider] when it is missing or
     * within [REFRESH_SKEW_SECONDS] of expiry.
     */
    internal fun current(): String? {
        val held = token.get()
        val nowSeconds = System.currentTimeMillis() / 1000
        val stillFresh = held != null && expiresAtSeconds.get() - REFRESH_SKEW_SECONDS > nowSeconds
        if (stillFresh) return held

        val fetch = provider ?: return held // No provider: whatever was set, even if stale.

        return synchronized(this) {
            // Another thread may have refreshed while this one waited.
            val latest = token.get()
            if (latest != null && expiresAtSeconds.get() - REFRESH_SKEW_SECONDS > System.currentTimeMillis() / 1000) {
                latest
            } else {
                try {
                    fetch()?.also { store(it) } ?: latest
                } catch (e: Exception) {
                    RiviumSyncLogger.e("User token provider failed: ${e.message}")
                    latest // Let the server decide; the old token may still work.
                }
            }
        }
    }

    /** The user the current token is for (its `sub`), or null without one. */
    internal fun currentUser(): String? {
        val jwt = current() ?: return null
        return Regex("\"sub\"\\s*:\\s*\"([^\"]*)\"").find(payloadOf(jwt) ?: return null)?.groupValues?.get(1)
    }

    /** Expiry from the JWT payload, or 0 when it cannot be read. */
    private fun expiryOf(jwt: String): Long {
        val decoded = payloadOf(jwt) ?: return 0L
        return Regex("\"exp\"\\s*:\\s*(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun payloadOf(jwt: String): String? {
        return try {
            val payload = jwt.split(".").getOrNull(1) ?: return null
            String(
                android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            )
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        /** Refresh this many seconds before the token actually expires. */
        const val REFRESH_SKEW_SECONDS = 60L
    }
}
