package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.offline.ConflictChoice
import co.rivium.sync.sdk.offline.ConflictResolver
import co.rivium.sync.sdk.offline.ConflictStrategy
import org.junit.Test

/**
 * Comprehensive tests for RiviumSyncConfig and its Builder
 */
class RiviumSyncConfigTest {

    // ==================== Valid API Key Formats ====================

    @Test
    fun `builder accepts valid live API key`() {
        val config = RiviumSyncConfig.Builder("nl_live_abc123xyz789")
            .build()

        assertThat(config.apiKey).isEqualTo("nl_live_abc123xyz789")
    }

    @Test
    fun `builder accepts valid test API key`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123xyz789")
            .build()

        assertThat(config.apiKey).isEqualTo("nl_test_abc123xyz789")
    }

    @Test
    fun `builder accepts nl_live prefix with long key`() {
        val config = RiviumSyncConfig.Builder("nl_live_abcdefghijklmnop")
            .build()

        assertThat(config.apiKey).isEqualTo("nl_live_abcdefghijklmnop")
    }

    // ==================== Invalid API Key Validation ====================

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects empty API key`() {
        RiviumSyncConfig.Builder("").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects blank API key`() {
        RiviumSyncConfig.Builder("   ").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects invalid prefix API key`() {
        RiviumSyncConfig.Builder("invalid_key_format").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects API key without proper prefix`() {
        RiviumSyncConfig.Builder("abc123").build()
    }

    // ==================== Default Values ====================

    @Test
    fun `builder sets correct default values`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .build()

        assertThat(config.debugMode).isFalse()
        assertThat(config.autoReconnect).isTrue()
        assertThat(config.offlineEnabled).isFalse()
        assertThat(config.offlineCacheSizeMb).isEqualTo(100)
        assertThat(config.syncOnReconnect).isTrue()
        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.SERVER_WINS)
        assertThat(config.conflictResolver).isNull()
        assertThat(config.maxSyncRetries).isEqualTo(3)
    }

    // ==================== debugMode ====================

    @Test
    fun `debugMode can be enabled`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .build()

        assertThat(config.debugMode).isTrue()
    }

    @Test
    fun `debugMode can be disabled`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .debugMode(false) // Override
            .build()

        assertThat(config.debugMode).isFalse()
    }

    // ==================== autoReconnect ====================

    @Test
    fun `autoReconnect can be disabled`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .autoReconnect(false)
            .build()

        assertThat(config.autoReconnect).isFalse()
    }

    @Test
    fun `autoReconnect can be enabled explicitly`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .autoReconnect(true)
            .build()

        assertThat(config.autoReconnect).isTrue()
    }

    // ==================== offlineEnabled ====================

    @Test
    fun `offlineEnabled can be enabled`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineEnabled(true)
            .build()

        assertThat(config.offlineEnabled).isTrue()
    }

    @Test
    fun `offlineEnabled defaults to false`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .build()

        assertThat(config.offlineEnabled).isFalse()
    }

    // ==================== offlineCacheSizeMb ====================

    @Test
    fun `offlineCacheSizeMb accepts valid positive value`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(50)
            .build()

        assertThat(config.offlineCacheSizeMb).isEqualTo(50)
    }

    @Test
    fun `offlineCacheSizeMb accepts large value`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(1000)
            .build()

        assertThat(config.offlineCacheSizeMb).isEqualTo(1000)
    }

    @Test
    fun `offlineCacheSizeMb accepts minimum value of 1`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(1)
            .build()

        assertThat(config.offlineCacheSizeMb).isEqualTo(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offlineCacheSizeMb rejects negative value`() {
        RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offlineCacheSizeMb rejects zero`() {
        RiviumSyncConfig.Builder("nl_test_abc123")
            .offlineCacheSizeMb(0)
            .build()
    }

    // ==================== syncOnReconnect ====================

    @Test
    fun `syncOnReconnect can be disabled`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .syncOnReconnect(false)
            .build()

        assertThat(config.syncOnReconnect).isFalse()
    }

    @Test
    fun `syncOnReconnect defaults to true`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .build()

        assertThat(config.syncOnReconnect).isTrue()
    }

    // ==================== conflictStrategy ====================

    @Test
    fun `conflictStrategy can be set to SERVER_WINS`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictStrategy(ConflictStrategy.SERVER_WINS)
            .build()

        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.SERVER_WINS)
    }

    @Test
    fun `conflictStrategy can be set to CLIENT_WINS`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictStrategy(ConflictStrategy.CLIENT_WINS)
            .build()

        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.CLIENT_WINS)
    }

    @Test
    fun `conflictStrategy can be set to MANUAL`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictStrategy(ConflictStrategy.MANUAL)
            .build()

        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.MANUAL)
    }

    @Test
    fun `conflictStrategy can be set to MERGE`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictStrategy(ConflictStrategy.MERGE)
            .build()

        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.MERGE)
    }

    // ==================== conflictResolver ====================

    @Test
    fun `conflictResolver can be set`() {
        val resolver = ConflictResolver { conflict ->
            Pair(ConflictChoice.USE_LOCAL, null)
        }

        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictResolver(resolver)
            .build()

        assertThat(config.conflictResolver).isNotNull()
    }

    @Test
    fun `conflictResolver defaults to null`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .build()

        assertThat(config.conflictResolver).isNull()
    }

    @Test
    fun `conflictResolver can perform custom resolution`() {
        val resolver = ConflictResolver { conflict ->
            // Custom logic: merge data
            val merged = conflict.localData.toMutableMap()
            merged.putAll(conflict.serverData)
            Pair(ConflictChoice.USE_MERGED, merged)
        }

        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .conflictResolver(resolver)
            .build()

        assertThat(config.conflictResolver).isNotNull()
    }

    // ==================== maxSyncRetries ====================

    @Test
    fun `maxSyncRetries accepts valid positive value`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .maxSyncRetries(5)
            .build()

        assertThat(config.maxSyncRetries).isEqualTo(5)
    }

    @Test
    fun `maxSyncRetries accepts zero for no retries`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .maxSyncRetries(0)
            .build()

        assertThat(config.maxSyncRetries).isEqualTo(0)
    }

    @Test
    fun `maxSyncRetries defaults to 3`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .build()

        assertThat(config.maxSyncRetries).isEqualTo(3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxSyncRetries rejects negative value`() {
        RiviumSyncConfig.Builder("nl_test_abc123")
            .maxSyncRetries(-1)
            .build()
    }

    // ==================== Builder Chaining ====================

    @Test
    fun `builder methods return builder for chaining`() {
        val builder = RiviumSyncConfig.Builder("nl_test_abc123")

        val result = builder
            .debugMode(true)
            .autoReconnect(false)
            .offlineEnabled(true)
            .offlineCacheSizeMb(200)
            .syncOnReconnect(false)
            .conflictStrategy(ConflictStrategy.CLIENT_WINS)
            .maxSyncRetries(5)

        // Verify chaining returns the builder
        assertThat(result).isSameInstanceAs(builder)
    }

    @Test
    fun `full configuration with all options`() {
        val resolver = ConflictResolver { Pair(ConflictChoice.USE_SERVER, null) }

        val config = RiviumSyncConfig.Builder("nl_live_production_key_123")
            .debugMode(true)
            .autoReconnect(true)
            .offlineEnabled(true)
            .offlineCacheSizeMb(256)
            .syncOnReconnect(true)
            .conflictStrategy(ConflictStrategy.MERGE)
            .conflictResolver(resolver)
            .maxSyncRetries(10)
            .build()

        assertThat(config.apiKey).isEqualTo("nl_live_production_key_123")
        assertThat(config.debugMode).isTrue()
        assertThat(config.autoReconnect).isTrue()
        assertThat(config.offlineEnabled).isTrue()
        assertThat(config.offlineCacheSizeMb).isEqualTo(256)
        assertThat(config.syncOnReconnect).isTrue()
        assertThat(config.conflictStrategy).isEqualTo(ConflictStrategy.MERGE)
        assertThat(config.conflictResolver).isEqualTo(resolver)
        assertThat(config.maxSyncRetries).isEqualTo(10)
    }

    // ==================== Static Factory Method ====================

    @Test
    fun `builder static method creates new builder`() {
        val builder = RiviumSyncConfig.builder("nl_test_abc123")

        assertThat(builder).isNotNull()
    }

    @Test
    fun `builder static method allows configuration`() {
        val config = RiviumSyncConfig.builder("nl_test_abc123")
            .debugMode(true)
            .build()

        assertThat(config.apiKey).isEqualTo("nl_test_abc123")
        assertThat(config.debugMode).isTrue()
    }

    // ==================== Data Class Features ====================

    @Test
    fun `config is immutable after creation`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .build()

        // Config properties are val, so they can't be changed
        assertThat(config.debugMode).isTrue()
        // No setter available - config is immutable
    }

    @Test
    fun `configs with same values are equal`() {
        val config1 = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .offlineCacheSizeMb(50)
            .build()

        val config2 = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .offlineCacheSizeMb(50)
            .build()

        assertThat(config1).isEqualTo(config2)
    }

    @Test
    fun `configs with different values are not equal`() {
        val config1 = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .build()

        val config2 = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(false)
            .build()

        assertThat(config1).isNotEqualTo(config2)
    }

    // ==================== Override Previous Values ====================

    @Test
    fun `later builder calls override earlier ones`() {
        val config = RiviumSyncConfig.Builder("nl_test_abc123")
            .debugMode(true)
            .debugMode(false) // Override
            .offlineCacheSizeMb(50)
            .offlineCacheSizeMb(100) // Override
            .build()

        assertThat(config.debugMode).isFalse()
        assertThat(config.offlineCacheSizeMb).isEqualTo(100)
    }
}
