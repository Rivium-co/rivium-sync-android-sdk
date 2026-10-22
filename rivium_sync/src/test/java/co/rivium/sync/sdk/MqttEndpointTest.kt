package co.rivium.sync.sdk

import co.rivium.sync.sdk.internal.MqttEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttEndpointTest {
    private val default = MqttEndpoint("mqtt-sync.rivium.co", 8885, true)

    @Test
    fun `uses the server's endpoint when it is valid and encrypted`() {
        assertEquals(MqttEndpoint("mqtt-sync.rivium.co", 8885, true), MqttEndpoint.resolve("mqtt-sync.rivium.co", 8885, true, default))
        assertEquals(MqttEndpoint("rivium.co", 443, true), MqttEndpoint.resolve("Rivium.co ", 443, true, default))
    }

    @Test
    fun `never connects unencrypted, whatever the server answers`() {
        assertEquals(default, MqttEndpoint.resolve("mqtt-sync.rivium.co", 1884, false, default))
        assertEquals(default, MqttEndpoint.resolve("other.rivium.co", 1884, false, default))
        assertEquals(true, default.useTls)
        assertEquals(MqttEndpoint("mqtt-sync.rivium.co", 8885, true), MqttEndpoint(RiviumSyncConfig.SYNC_HOST, RiviumSyncConfig.SYNC_PORT, RiviumSyncConfig.SYNC_USE_TLS))
    }

    @Test
    fun `falls back to the built-in endpoint when the answer is missing or incomplete (older server)`() {
        assertEquals(default, MqttEndpoint.resolve(null, null, null, default))
        assertEquals(default, MqttEndpoint.resolve("mqtt-sync.rivium.co", null, true, default))
        assertEquals(default, MqttEndpoint.resolve("mqtt-sync.rivium.co", 8885, null, default))
        assertEquals(default, MqttEndpoint.resolve("", 8885, true, default))
    }

    @Test
    fun `never sends the token to another domain or an invalid port`() {
        for (host in listOf("evil.com", "rivium.co.evil.com", "evilrivium.co", "mqtt-sync.rivium.com", "10.0.0.1", "mqtt sync.rivium.co", "-x.rivium.co")) {
            assertEquals(host, default, MqttEndpoint.resolve(host, 8885, true, default))
        }
        for (port in listOf(0, -1, 65536)) {
            assertEquals(default, MqttEndpoint.resolve("mqtt-sync.rivium.co", port, true, default))
        }
    }
}
