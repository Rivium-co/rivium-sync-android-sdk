package co.rivium.example

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stands in for YOUR backend, so this example can run against a project that
 * has "Require signed user tokens" turned on.
 *
 * Security Rules read `auth.uid`. An app cannot decide that value: its API key
 * ships inside the APK, so anyone could claim any user. Only a server holding
 * the project's server secret can mint a user token.
 *
 * In a real app, replace [fetchToken] with a call to your own endpoint, behind
 * your own login, that mints a token for the user who is actually signed in
 * (the Node SDK's `createUserToken` does this). The server secret must never be
 * in an app - this example only reads it in debug builds, from local.properties.
 */
object DemoTokenMinter {
    private const val TAG = "DemoTokenMinter"
    private const val API_URL = "https://sync.rivium.co"

    /** True when local.properties gave this debug build a server secret. */
    val isConfigured: Boolean
        get() = BuildConfig.DEMO_SERVER_SECRET.isNotEmpty()

    /**
     * Mints a token for the demo user. Called by the SDK on a background
     * thread: at first use, shortly before the token expires, and again if the
     * server says it expired.
     */
    fun fetchToken(): String? {
        if (!isConfigured) return null

        val connection = URL("$API_URL/users/token").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", AppConfig.apiKey)
            connection.setRequestProperty("x-server-secret", BuildConfig.DEMO_SERVER_SECRET)

            val body = JSONObject().put("userId", BuildConfig.DEMO_USER_ID).toString()
            connection.outputStream.use { it.write(body.toByteArray()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                Log.e(TAG, "Token request failed ($code): $text")
                return null
            }
            JSONObject(text).getString("token").also {
                Log.d(TAG, "Minted a user token for ${BuildConfig.DEMO_USER_ID}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not reach $API_URL: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }
}
