package co.rivium.sync.sdk.offline

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for offline package enums: SyncStatus, SyncState, OperationType
 */
class OfflineEnumsTest {

    // ==================== SyncStatus ====================

    @Test
    fun `SyncStatus has all expected values`() {
        val values = SyncStatus.values()

        assertThat(values).hasLength(5)
        assertThat(values).asList().containsExactly(
            SyncStatus.SYNCED,
            SyncStatus.PENDING_CREATE,
            SyncStatus.PENDING_UPDATE,
            SyncStatus.PENDING_DELETE,
            SyncStatus.SYNC_FAILED
        )
    }

    @Test
    fun `SyncStatus SYNCED represents synced state`() {
        val status = SyncStatus.SYNCED
        assertThat(status.name).isEqualTo("SYNCED")
        assertThat(status.ordinal).isEqualTo(0)
    }

    @Test
    fun `SyncStatus PENDING_CREATE represents pending create`() {
        val status = SyncStatus.PENDING_CREATE
        assertThat(status.name).isEqualTo("PENDING_CREATE")
    }

    @Test
    fun `SyncStatus PENDING_UPDATE represents pending update`() {
        val status = SyncStatus.PENDING_UPDATE
        assertThat(status.name).isEqualTo("PENDING_UPDATE")
    }

    @Test
    fun `SyncStatus PENDING_DELETE represents pending delete`() {
        val status = SyncStatus.PENDING_DELETE
        assertThat(status.name).isEqualTo("PENDING_DELETE")
    }

    @Test
    fun `SyncStatus SYNC_FAILED represents sync failure`() {
        val status = SyncStatus.SYNC_FAILED
        assertThat(status.name).isEqualTo("SYNC_FAILED")
    }

    @Test
    fun `SyncStatus valueOf returns correct value`() {
        assertThat(SyncStatus.valueOf("SYNCED")).isEqualTo(SyncStatus.SYNCED)
        assertThat(SyncStatus.valueOf("PENDING_CREATE")).isEqualTo(SyncStatus.PENDING_CREATE)
        assertThat(SyncStatus.valueOf("PENDING_UPDATE")).isEqualTo(SyncStatus.PENDING_UPDATE)
        assertThat(SyncStatus.valueOf("PENDING_DELETE")).isEqualTo(SyncStatus.PENDING_DELETE)
        assertThat(SyncStatus.valueOf("SYNC_FAILED")).isEqualTo(SyncStatus.SYNC_FAILED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SyncStatus valueOf throws for invalid value`() {
        SyncStatus.valueOf("INVALID")
    }

    // ==================== SyncState ====================

    @Test
    fun `SyncState has all expected values`() {
        val values = SyncState.values()

        assertThat(values).hasLength(4)
        assertThat(values).asList().containsExactly(
            SyncState.IDLE,
            SyncState.SYNCING,
            SyncState.OFFLINE,
            SyncState.ERROR
        )
    }

    @Test
    fun `SyncState IDLE represents idle state`() {
        val state = SyncState.IDLE
        assertThat(state.name).isEqualTo("IDLE")
    }

    @Test
    fun `SyncState SYNCING represents syncing state`() {
        val state = SyncState.SYNCING
        assertThat(state.name).isEqualTo("SYNCING")
    }

    @Test
    fun `SyncState OFFLINE represents offline state`() {
        val state = SyncState.OFFLINE
        assertThat(state.name).isEqualTo("OFFLINE")
    }

    @Test
    fun `SyncState ERROR represents error state`() {
        val state = SyncState.ERROR
        assertThat(state.name).isEqualTo("ERROR")
    }

    @Test
    fun `SyncState valueOf returns correct value`() {
        assertThat(SyncState.valueOf("IDLE")).isEqualTo(SyncState.IDLE)
        assertThat(SyncState.valueOf("SYNCING")).isEqualTo(SyncState.SYNCING)
        assertThat(SyncState.valueOf("OFFLINE")).isEqualTo(SyncState.OFFLINE)
        assertThat(SyncState.valueOf("ERROR")).isEqualTo(SyncState.ERROR)
    }

    // ==================== OperationType ====================

    @Test
    fun `OperationType has all expected values`() {
        val values = OperationType.values()

        assertThat(values).hasLength(3)
        assertThat(values).asList().containsExactly(
            OperationType.CREATE,
            OperationType.UPDATE,
            OperationType.DELETE
        )
    }

    @Test
    fun `OperationType CREATE represents create operation`() {
        val type = OperationType.CREATE
        assertThat(type.name).isEqualTo("CREATE")
    }

    @Test
    fun `OperationType UPDATE represents update operation`() {
        val type = OperationType.UPDATE
        assertThat(type.name).isEqualTo("UPDATE")
    }

    @Test
    fun `OperationType DELETE represents delete operation`() {
        val type = OperationType.DELETE
        assertThat(type.name).isEqualTo("DELETE")
    }

    @Test
    fun `OperationType valueOf returns correct value`() {
        assertThat(OperationType.valueOf("CREATE")).isEqualTo(OperationType.CREATE)
        assertThat(OperationType.valueOf("UPDATE")).isEqualTo(OperationType.UPDATE)
        assertThat(OperationType.valueOf("DELETE")).isEqualTo(OperationType.DELETE)
    }

    // ==================== Exhaustive When Expressions ====================

    @Test
    fun `SyncStatus can be used in exhaustive when`() {
        val statuses = SyncStatus.values()

        statuses.forEach { status ->
            val description = when (status) {
                SyncStatus.SYNCED -> "synced"
                SyncStatus.PENDING_CREATE -> "pending create"
                SyncStatus.PENDING_UPDATE -> "pending update"
                SyncStatus.PENDING_DELETE -> "pending delete"
                SyncStatus.SYNC_FAILED -> "failed"
            }
            assertThat(description).isNotEmpty()
        }
    }

    @Test
    fun `SyncState can be used in exhaustive when`() {
        val states = SyncState.values()

        states.forEach { state ->
            val description = when (state) {
                SyncState.IDLE -> "idle"
                SyncState.SYNCING -> "syncing"
                SyncState.OFFLINE -> "offline"
                SyncState.ERROR -> "error"
            }
            assertThat(description).isNotEmpty()
        }
    }

    @Test
    fun `OperationType can be used in exhaustive when`() {
        val types = OperationType.values()

        types.forEach { type ->
            val description = when (type) {
                OperationType.CREATE -> "create"
                OperationType.UPDATE -> "update"
                OperationType.DELETE -> "delete"
            }
            assertThat(description).isNotEmpty()
        }
    }

    // ==================== Enum Comparisons ====================

    @Test
    fun `SyncStatus comparisons work correctly`() {
        assertThat(SyncStatus.SYNCED).isEqualTo(SyncStatus.SYNCED)
        assertThat(SyncStatus.SYNCED).isNotEqualTo(SyncStatus.PENDING_CREATE)
    }

    @Test
    fun `SyncState comparisons work correctly`() {
        assertThat(SyncState.IDLE).isEqualTo(SyncState.IDLE)
        assertThat(SyncState.IDLE).isNotEqualTo(SyncState.SYNCING)
    }

    @Test
    fun `OperationType comparisons work correctly`() {
        assertThat(OperationType.CREATE).isEqualTo(OperationType.CREATE)
        assertThat(OperationType.CREATE).isNotEqualTo(OperationType.DELETE)
    }

    // ==================== Pending Status Checks ====================

    @Test
    fun `pending statuses can be identified`() {
        val pendingStatuses = setOf(
            SyncStatus.PENDING_CREATE,
            SyncStatus.PENDING_UPDATE,
            SyncStatus.PENDING_DELETE
        )

        SyncStatus.values().forEach { status ->
            val isPending = status in pendingStatuses
            when (status) {
                SyncStatus.SYNCED -> assertThat(isPending).isFalse()
                SyncStatus.SYNC_FAILED -> assertThat(isPending).isFalse()
                else -> assertThat(isPending).isTrue()
            }
        }
    }
}
