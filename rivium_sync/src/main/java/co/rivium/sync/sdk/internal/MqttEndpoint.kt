package co.rivium.sync.sdk.internal

/**
 * Where the realtime connection goes: the host, port and TLS setting the Sync API returns with the connection token,
 * so they can change on the server (for example to an encrypted port) without a new SDK.
 *
 * The answer is used only when it is valid: a host of rivium.co (or one of its subdomains) and a port from 1 to
 * 65535. Anything else — no answer from an older server, a missing field, another domain — falls back to the
 * built-in values, so the connection token is never sent anywhere but Rivium.
 */
internal data class MqttEndpoint(val host: String, val port: Int, val useTls: Boolean) {
    companion object {
        private val HOST = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*rivium\\.co$")

        fun resolve(host: String?, port: Int?, useTls: Boolean?, default: MqttEndpoint): MqttEndpoint {
            val h = host?.trim()?.lowercase()
            if (h.isNullOrEmpty() || h.length > 253 || !HOST.matches(h)) return default
            if (port == null || port !in 1..65535) return default
            if (useTls == null) return default
            return MqttEndpoint(h, port, useTls)
        }
    }
}
