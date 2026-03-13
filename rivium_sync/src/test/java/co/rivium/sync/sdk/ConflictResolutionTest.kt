package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.offline.*
import org.junit.Test

/**
 * Unit tests for Conflict Resolution
 *
 * These tests verify:
 * - ConflictStrategy enum values
 * - ConflictResult sealed class variants
 * - ConflictInfo data class
 * - ConflictResolver interface
 */
class ConflictResolutionTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `ConflictStrategy has all expected values`() {
        val strategies = ConflictStrategy.values()

        assertThat(strategies).hasLength(4)
        assertThat(strategies).asList().containsExactly(
            ConflictStrategy.SERVER_WINS,
            ConflictStrategy.CLIENT_WINS,
            ConflictStrategy.MANUAL,
            ConflictStrategy.MERGE
        )
    }

    @Test
    fun `ConflictChoice has all expected values`() {
        val choices = ConflictChoice.values()

        assertThat(choices).hasLength(3)
        assertThat(choices).asList().containsExactly(
            ConflictChoice.USE_LOCAL,
            ConflictChoice.USE_SERVER,
            ConflictChoice.USE_MERGED
        )
    }

    @Test
    fun `ConflictInfo contains all required fields`() {
        val localData = mapOf("name" to "Local", "count" to 5)
        val serverData = mapOf("name" to "Server", "count" to 10)

        val conflictInfo = ConflictInfo(
            documentId = "doc123",
            databaseId = "db1",
            collectionId = "users",
            localData = localData,
            serverData = serverData,
            localVersion = 2,
            serverVersion = 3
        )

        assertThat(conflictInfo.documentId).isEqualTo("doc123")
        assertThat(conflictInfo.databaseId).isEqualTo("db1")
        assertThat(conflictInfo.collectionId).isEqualTo("users")
        assertThat(conflictInfo.localData).isEqualTo(localData)
        assertThat(conflictInfo.serverData).isEqualTo(serverData)
        assertThat(conflictInfo.localVersion).isEqualTo(2)
        assertThat(conflictInfo.serverVersion).isEqualTo(3)
    }

    @Test
    fun `ConflictResult NoConflict contains document`() {
        val document = SyncDocument(
            id = "doc1",
            data = mapOf("name" to "Test"),
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val result = ConflictResult.NoConflict(document)

        assertThat(result.document).isEqualTo(document)
    }

    @Test
    fun `ConflictResult ServerWins contains server document`() {
        val serverDoc = SyncDocument(
            id = "doc1",
            data = mapOf("name" to "Server"),
            createdAt = now,
            updatedAt = now,
            version = 5
        )

        val result = ConflictResult.ServerWins(serverDoc)

        assertThat(result.serverDocument).isEqualTo(serverDoc)
        assertThat(result.serverDocument.data["name"]).isEqualTo("Server")
    }

    @Test
    fun `ConflictResult ClientWins contains client document`() {
        val clientDoc = SyncDocument(
            id = "doc1",
            data = mapOf("name" to "Client"),
            createdAt = now,
            updatedAt = now,
            version = 3
        )

        val result = ConflictResult.ClientWins(clientDoc)

        assertThat(result.clientDocument).isEqualTo(clientDoc)
        assertThat(result.clientDocument.data["name"]).isEqualTo("Client")
    }

    @Test
    fun `ConflictResult Merged contains merged document`() {
        val mergedDoc = SyncDocument(
            id = "doc1",
            data = mapOf("name" to "Client", "count" to 10), // Merged fields
            createdAt = now,
            updatedAt = now,
            version = 6
        )

        val result = ConflictResult.Merged(mergedDoc)

        assertThat(result.mergedDocument).isEqualTo(mergedDoc)
    }

    @Test
    fun `ConflictResult NeedsResolution contains conflict details`() {
        val localData = mapOf("name" to "Local")
        val serverData = mapOf("name" to "Server")

        val result = ConflictResult.NeedsResolution(
            documentId = "doc1",
            localData = localData,
            serverData = serverData,
            localVersion = 2,
            serverVersion = 3
        )

        assertThat(result.documentId).isEqualTo("doc1")
        assertThat(result.localData).isEqualTo(localData)
        assertThat(result.serverData).isEqualTo(serverData)
        assertThat(result.localVersion).isEqualTo(2)
        assertThat(result.serverVersion).isEqualTo(3)
    }

    @Test
    fun `ConflictResolver can resolve with USE_LOCAL`() {
        val resolver = ConflictResolver { conflict ->
            Pair(ConflictChoice.USE_LOCAL, null)
        }

        val conflict = ConflictInfo(
            documentId = "doc1",
            databaseId = "db1",
            collectionId = "users",
            localData = mapOf("name" to "Local"),
            serverData = mapOf("name" to "Server"),
            localVersion = 2,
            serverVersion = 3
        )

        val (choice, data) = resolver.resolve(conflict)

        assertThat(choice).isEqualTo(ConflictChoice.USE_LOCAL)
        assertThat(data).isNull()
    }

    @Test
    fun `ConflictResolver can resolve with USE_SERVER`() {
        val resolver = ConflictResolver { conflict ->
            Pair(ConflictChoice.USE_SERVER, null)
        }

        val conflict = ConflictInfo(
            documentId = "doc1",
            databaseId = "db1",
            collectionId = "users",
            localData = mapOf("name" to "Local"),
            serverData = mapOf("name" to "Server"),
            localVersion = 2,
            serverVersion = 3
        )

        val (choice, data) = resolver.resolve(conflict)

        assertThat(choice).isEqualTo(ConflictChoice.USE_SERVER)
    }

    @Test
    fun `ConflictResolver can resolve with USE_MERGED and custom data`() {
        val resolver = ConflictResolver { conflict ->
            // Merge: take name from local, count from server
            val merged = conflict.localData.toMutableMap()
            conflict.serverData["count"]?.let { merged["count"] = it }
            Pair(ConflictChoice.USE_MERGED, merged)
        }

        val conflict = ConflictInfo(
            documentId = "doc1",
            databaseId = "db1",
            collectionId = "users",
            localData = mapOf("name" to "Local", "count" to 5),
            serverData = mapOf("name" to "Server", "count" to 10),
            localVersion = 2,
            serverVersion = 3
        )

        val (choice, mergedData) = resolver.resolve(conflict)

        assertThat(choice).isEqualTo(ConflictChoice.USE_MERGED)
        assertThat(mergedData).isNotNull()
        assertThat(mergedData!!["name"]).isEqualTo("Local")
        assertThat(mergedData["count"]).isEqualTo(10)
    }

    @Test
    fun `ConflictResolver lambda can use conflict info to make decision`() {
        // Resolver that prefers higher version
        val resolver = ConflictResolver { conflict ->
            if (conflict.localVersion > conflict.serverVersion) {
                Pair(ConflictChoice.USE_LOCAL, null)
            } else {
                Pair(ConflictChoice.USE_SERVER, null)
            }
        }

        val conflictServerNewer = ConflictInfo(
            documentId = "doc1",
            databaseId = "db1",
            collectionId = "users",
            localData = mapOf("v" to "local"),
            serverData = mapOf("v" to "server"),
            localVersion = 2,
            serverVersion = 5
        )

        val conflictLocalNewer = ConflictInfo(
            documentId = "doc2",
            databaseId = "db1",
            collectionId = "users",
            localData = mapOf("v" to "local"),
            serverData = mapOf("v" to "server"),
            localVersion = 7,
            serverVersion = 3
        )

        val (choice1, _) = resolver.resolve(conflictServerNewer)
        val (choice2, _) = resolver.resolve(conflictLocalNewer)

        assertThat(choice1).isEqualTo(ConflictChoice.USE_SERVER)
        assertThat(choice2).isEqualTo(ConflictChoice.USE_LOCAL)
    }

    @Test
    fun `ConflictResult is sealed class with all expected subclasses`() {
        // Verify exhaustive when handling
        val results = listOf<ConflictResult>(
            ConflictResult.NoConflict(SyncDocument("1", mapOf(), now, now, 1)),
            ConflictResult.ServerWins(SyncDocument("2", mapOf(), now, now, 2)),
            ConflictResult.ClientWins(SyncDocument("3", mapOf(), now, now, 3)),
            ConflictResult.Merged(SyncDocument("4", mapOf(), now, now, 4)),
            ConflictResult.NeedsResolution("5", mapOf(), mapOf(), 1, 2)
        )

        results.forEach { result ->
            val description = when (result) {
                is ConflictResult.NoConflict -> "no conflict"
                is ConflictResult.ServerWins -> "server wins"
                is ConflictResult.ClientWins -> "client wins"
                is ConflictResult.Merged -> "merged"
                is ConflictResult.NeedsResolution -> "needs resolution"
            }
            assertThat(description).isNotEmpty()
        }
    }
}
