package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for SyncCollection, SyncDatabase, SyncQuery, and ListenerRegistration interfaces,
 * as well as QueryOperator and OrderDirection enums.
 *
 * These tests use lightweight mock implementations to verify that the interfaces
 * can be properly implemented and that enum values are correct.
 */
class SyncInterfacesTest {

    // ==================== Mock Implementations ====================

    /**
     * Minimal mock of ListenerRegistration that tracks whether remove() was called.
     */
    private class MockListenerRegistration(
        private val onRemove: () -> Unit = {}
    ) : ListenerRegistration {
        var removed = false
            private set

        override fun remove() {
            removed = true
            onRemove()
        }
    }

    /**
     * Minimal mock of SyncQuery that records chained calls.
     */
    private class MockSyncQuery : SyncQuery {
        val whereClauses = mutableListOf<Triple<String, QueryOperator, Any?>>()
        val orderByClauses = mutableListOf<Pair<String, OrderDirection>>()
        var limitValue: Int? = null
        var offsetValue: Int? = null

        override fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery {
            whereClauses.add(Triple(field, operator, value))
            return this
        }

        override fun orderBy(field: String, direction: OrderDirection): SyncQuery {
            orderByClauses.add(Pair(field, direction))
            return this
        }

        override fun limit(count: Int): SyncQuery {
            limitValue = count
            return this
        }

        override fun offset(count: Int): SyncQuery {
            offsetValue = count
            return this
        }

        override suspend fun get(): List<SyncDocument> = emptyList()

        override fun get(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit) {
            onSuccess(emptyList())
        }

        override fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration {
            return MockListenerRegistration()
        }
    }

    /**
     * Minimal mock of SyncCollection for interface compliance testing.
     */
    private class MockSyncCollection(
        override val id: String = "col-123",
        override val name: String = "users",
        override val databaseId: String = "db-456"
    ) : SyncCollection {
        val addedDocuments = mutableListOf<Map<String, Any?>>()
        val deletedIds = mutableListOf<String>()
        val listeners = mutableListOf<ListenerRegistration>()

        private val mockQuery = MockSyncQuery()

        override suspend fun add(data: Map<String, Any?>): SyncDocument {
            addedDocuments.add(data)
            return SyncDocument(
                id = "generated-id",
                data = data,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        override suspend fun get(documentId: String): SyncDocument? {
            return if (documentId == "existing") SyncDocument(
                id = documentId,
                data = mapOf("name" to "Test"),
                createdAt = 1000L,
                updatedAt = 2000L
            ) else null
        }

        override suspend fun getAll(): List<SyncDocument> = emptyList()

        override suspend fun update(documentId: String, data: Map<String, Any?>): SyncDocument {
            return SyncDocument(id = documentId, data = data, createdAt = 1000L, updatedAt = System.currentTimeMillis())
        }

        override suspend fun set(documentId: String, data: Map<String, Any?>): SyncDocument {
            return SyncDocument(id = documentId, data = data, createdAt = 1000L, updatedAt = System.currentTimeMillis())
        }

        override suspend fun delete(documentId: String) {
            deletedIds.add(documentId)
        }

        override fun query(): SyncQuery = mockQuery

        override fun where(field: String, operator: QueryOperator, value: Any?): SyncQuery {
            return mockQuery.where(field, operator, value)
        }

        override fun listen(callback: (List<SyncDocument>) -> Unit): ListenerRegistration {
            val reg = MockListenerRegistration()
            listeners.add(reg)
            return reg
        }

        override fun listenDocument(documentId: String, callback: (SyncDocument?) -> Unit): ListenerRegistration {
            val reg = MockListenerRegistration()
            listeners.add(reg)
            return reg
        }

        override fun add(data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
            addedDocuments.add(data)
            onSuccess(SyncDocument(id = "generated-id", data = data, createdAt = 1000L, updatedAt = 1000L))
        }

        override fun get(documentId: String, onSuccess: (SyncDocument?) -> Unit, onError: (Throwable) -> Unit) {
            onSuccess(null)
        }

        override fun getAll(onSuccess: (List<SyncDocument>) -> Unit, onError: (Throwable) -> Unit) {
            onSuccess(emptyList())
        }

        override fun update(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
            onSuccess(SyncDocument(id = documentId, data = data, createdAt = 1000L, updatedAt = 1000L))
        }

        override fun set(documentId: String, data: Map<String, Any?>, onSuccess: (SyncDocument) -> Unit, onError: (Throwable) -> Unit) {
            onSuccess(SyncDocument(id = documentId, data = data, createdAt = 1000L, updatedAt = 1000L))
        }

        override fun delete(documentId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
            deletedIds.add(documentId)
            onSuccess()
        }
    }

    /**
     * Minimal mock of SyncDatabase for interface compliance testing.
     */
    private class MockSyncDatabase(
        override val id: String = "db-456",
        override val name: String = "my-database"
    ) : SyncDatabase {
        val createdCollections = mutableListOf<String>()
        val deletedCollections = mutableListOf<String>()

        override fun collection(collectionIdOrName: String): SyncCollection {
            return MockSyncCollection(id = collectionIdOrName, name = collectionIdOrName, databaseId = id)
        }

        override suspend fun listCollections(): List<CollectionInfo> = emptyList()

        override suspend fun createCollection(name: String): SyncCollection {
            createdCollections.add(name)
            return MockSyncCollection(id = "new-col-id", name = name, databaseId = id)
        }

        override suspend fun deleteCollection(collectionId: String) {
            deletedCollections.add(collectionId)
        }
    }

    // ==================== QueryOperator Enum Tests ====================

    @Test
    fun `QueryOperator has exactly 9 entries`() {
        assertThat(QueryOperator.entries).hasSize(9)
    }

    @Test
    fun `QueryOperator contains all expected members`() {
        assertThat(QueryOperator.entries).containsExactly(
            QueryOperator.EQUAL,
            QueryOperator.NOT_EQUAL,
            QueryOperator.GREATER_THAN,
            QueryOperator.GREATER_THAN_OR_EQUAL,
            QueryOperator.LESS_THAN,
            QueryOperator.LESS_THAN_OR_EQUAL,
            QueryOperator.ARRAY_CONTAINS,
            QueryOperator.IN,
            QueryOperator.NOT_IN
        )
    }

    @Test
    fun `QueryOperator EQUAL has value ==`() {
        assertThat(QueryOperator.EQUAL.value).isEqualTo("==")
    }

    @Test
    fun `QueryOperator NOT_EQUAL has value !=`() {
        assertThat(QueryOperator.NOT_EQUAL.value).isEqualTo("!=")
    }

    @Test
    fun `QueryOperator GREATER_THAN has value greater than symbol`() {
        assertThat(QueryOperator.GREATER_THAN.value).isEqualTo(">")
    }

    @Test
    fun `QueryOperator GREATER_THAN_OR_EQUAL has value greater than or equal symbol`() {
        assertThat(QueryOperator.GREATER_THAN_OR_EQUAL.value).isEqualTo(">=")
    }

    @Test
    fun `QueryOperator LESS_THAN has value less than symbol`() {
        assertThat(QueryOperator.LESS_THAN.value).isEqualTo("<")
    }

    @Test
    fun `QueryOperator LESS_THAN_OR_EQUAL has value less than or equal symbol`() {
        assertThat(QueryOperator.LESS_THAN_OR_EQUAL.value).isEqualTo("<=")
    }

    @Test
    fun `QueryOperator ARRAY_CONTAINS has value array-contains`() {
        assertThat(QueryOperator.ARRAY_CONTAINS.value).isEqualTo("array-contains")
    }

    @Test
    fun `QueryOperator IN has value in`() {
        assertThat(QueryOperator.IN.value).isEqualTo("in")
    }

    @Test
    fun `QueryOperator NOT_IN has value not-in`() {
        assertThat(QueryOperator.NOT_IN.value).isEqualTo("not-in")
    }

    @Test
    fun `QueryOperator valueOf resolves all members`() {
        assertThat(QueryOperator.valueOf("EQUAL")).isEqualTo(QueryOperator.EQUAL)
        assertThat(QueryOperator.valueOf("NOT_EQUAL")).isEqualTo(QueryOperator.NOT_EQUAL)
        assertThat(QueryOperator.valueOf("GREATER_THAN")).isEqualTo(QueryOperator.GREATER_THAN)
        assertThat(QueryOperator.valueOf("GREATER_THAN_OR_EQUAL")).isEqualTo(QueryOperator.GREATER_THAN_OR_EQUAL)
        assertThat(QueryOperator.valueOf("LESS_THAN")).isEqualTo(QueryOperator.LESS_THAN)
        assertThat(QueryOperator.valueOf("LESS_THAN_OR_EQUAL")).isEqualTo(QueryOperator.LESS_THAN_OR_EQUAL)
        assertThat(QueryOperator.valueOf("ARRAY_CONTAINS")).isEqualTo(QueryOperator.ARRAY_CONTAINS)
        assertThat(QueryOperator.valueOf("IN")).isEqualTo(QueryOperator.IN)
        assertThat(QueryOperator.valueOf("NOT_IN")).isEqualTo(QueryOperator.NOT_IN)
    }

    @Test
    fun `QueryOperator ordinal values are sequential`() {
        QueryOperator.entries.forEachIndexed { index, operator ->
            assertThat(operator.ordinal).isEqualTo(index)
        }
    }

    // ==================== OrderDirection Enum Tests ====================

    @Test
    fun `OrderDirection has exactly 2 entries`() {
        assertThat(OrderDirection.entries).hasSize(2)
    }

    @Test
    fun `OrderDirection contains ASCENDING and DESCENDING`() {
        assertThat(OrderDirection.entries).containsExactly(
            OrderDirection.ASCENDING,
            OrderDirection.DESCENDING
        )
    }

    @Test
    fun `OrderDirection ASCENDING has value asc`() {
        assertThat(OrderDirection.ASCENDING.value).isEqualTo("asc")
    }

    @Test
    fun `OrderDirection DESCENDING has value desc`() {
        assertThat(OrderDirection.DESCENDING.value).isEqualTo("desc")
    }

    @Test
    fun `OrderDirection valueOf resolves all members`() {
        assertThat(OrderDirection.valueOf("ASCENDING")).isEqualTo(OrderDirection.ASCENDING)
        assertThat(OrderDirection.valueOf("DESCENDING")).isEqualTo(OrderDirection.DESCENDING)
    }

    // ==================== ListenerRegistration Tests ====================

    @Test
    fun `ListenerRegistration remove marks as removed`() {
        val registration = MockListenerRegistration()

        assertThat(registration.removed).isFalse()

        registration.remove()

        assertThat(registration.removed).isTrue()
    }

    @Test
    fun `ListenerRegistration remove invokes callback`() {
        var callbackInvoked = false
        val registration = MockListenerRegistration(onRemove = { callbackInvoked = true })

        registration.remove()

        assertThat(callbackInvoked).isTrue()
    }

    @Test
    fun `ListenerRegistration remove can be called multiple times`() {
        var callCount = 0
        val registration = MockListenerRegistration(onRemove = { callCount++ })

        registration.remove()
        registration.remove()
        registration.remove()

        assertThat(callCount).isEqualTo(3)
    }

    @Test
    fun `ListenerRegistration implements the interface`() {
        val registration: ListenerRegistration = MockListenerRegistration()

        assertThat(registration).isInstanceOf(ListenerRegistration::class.java)
    }

    // ==================== SyncQuery Fluent Chain Tests ====================

    @Test
    fun `SyncQuery where returns same query for chaining`() {
        val query = MockSyncQuery()

        val result = query.where("age", QueryOperator.GREATER_THAN, 18)

        assertThat(result).isSameInstanceAs(query)
    }

    @Test
    fun `SyncQuery orderBy returns same query for chaining`() {
        val query = MockSyncQuery()

        val result = query.orderBy("name", OrderDirection.ASCENDING)

        assertThat(result).isSameInstanceAs(query)
    }

    @Test
    fun `SyncQuery limit returns same query for chaining`() {
        val query = MockSyncQuery()

        val result = query.limit(10)

        assertThat(result).isSameInstanceAs(query)
    }

    @Test
    fun `SyncQuery offset returns same query for chaining`() {
        val query = MockSyncQuery()

        val result = query.offset(5)

        assertThat(result).isSameInstanceAs(query)
    }

    @Test
    fun `SyncQuery supports full fluent chain`() {
        val query = MockSyncQuery()

        val result = query
            .where("status", QueryOperator.EQUAL, "active")
            .where("age", QueryOperator.GREATER_THAN_OR_EQUAL, 21)
            .orderBy("name", OrderDirection.ASCENDING)
            .limit(25)
            .offset(10)

        assertThat(result).isSameInstanceAs(query)
        assertThat(query.whereClauses).hasSize(2)
        assertThat(query.orderByClauses).hasSize(1)
        assertThat(query.limitValue).isEqualTo(25)
        assertThat(query.offsetValue).isEqualTo(10)
    }

    @Test
    fun `SyncQuery where records field, operator, and value`() {
        val query = MockSyncQuery()

        query.where("score", QueryOperator.LESS_THAN, 100)

        assertThat(query.whereClauses).hasSize(1)
        assertThat(query.whereClauses[0].first).isEqualTo("score")
        assertThat(query.whereClauses[0].second).isEqualTo(QueryOperator.LESS_THAN)
        assertThat(query.whereClauses[0].third).isEqualTo(100)
    }

    @Test
    fun `SyncQuery where supports null value`() {
        val query = MockSyncQuery()

        query.where("deletedAt", QueryOperator.EQUAL, null)

        assertThat(query.whereClauses).hasSize(1)
        assertThat(query.whereClauses[0].third).isNull()
    }

    @Test
    fun `SyncQuery where supports list value for IN operator`() {
        val query = MockSyncQuery()
        val statusValues = listOf("active", "pending", "review")

        query.where("status", QueryOperator.IN, statusValues)

        assertThat(query.whereClauses).hasSize(1)
        assertThat(query.whereClauses[0].second).isEqualTo(QueryOperator.IN)
        assertThat(query.whereClauses[0].third).isEqualTo(statusValues)
    }

    @Test
    fun `SyncQuery where supports list value for NOT_IN operator`() {
        val query = MockSyncQuery()
        val excludedRoles = listOf("banned", "suspended")

        query.where("role", QueryOperator.NOT_IN, excludedRoles)

        assertThat(query.whereClauses).hasSize(1)
        assertThat(query.whereClauses[0].second).isEqualTo(QueryOperator.NOT_IN)
        assertThat(query.whereClauses[0].third).isEqualTo(excludedRoles)
    }

    @Test
    fun `SyncQuery where supports ARRAY_CONTAINS with string value`() {
        val query = MockSyncQuery()

        query.where("tags", QueryOperator.ARRAY_CONTAINS, "premium")

        assertThat(query.whereClauses).hasSize(1)
        assertThat(query.whereClauses[0].second).isEqualTo(QueryOperator.ARRAY_CONTAINS)
        assertThat(query.whereClauses[0].third).isEqualTo("premium")
    }

    @Test
    fun `SyncQuery orderBy records field and direction`() {
        val query = MockSyncQuery()

        query.orderBy("createdAt", OrderDirection.DESCENDING)

        assertThat(query.orderByClauses).hasSize(1)
        assertThat(query.orderByClauses[0].first).isEqualTo("createdAt")
        assertThat(query.orderByClauses[0].second).isEqualTo(OrderDirection.DESCENDING)
    }

    @Test
    fun `SyncQuery supports multiple orderBy clauses`() {
        val query = MockSyncQuery()

        query
            .orderBy("lastName", OrderDirection.ASCENDING)
            .orderBy("firstName", OrderDirection.ASCENDING)

        assertThat(query.orderByClauses).hasSize(2)
        assertThat(query.orderByClauses[0].first).isEqualTo("lastName")
        assertThat(query.orderByClauses[1].first).isEqualTo("firstName")
    }

    @Test
    fun `SyncQuery supports multiple where clauses with different operators`() {
        val query = MockSyncQuery()

        query
            .where("age", QueryOperator.GREATER_THAN_OR_EQUAL, 18)
            .where("age", QueryOperator.LESS_THAN, 65)
            .where("status", QueryOperator.NOT_EQUAL, "banned")

        assertThat(query.whereClauses).hasSize(3)
        assertThat(query.whereClauses[0].second).isEqualTo(QueryOperator.GREATER_THAN_OR_EQUAL)
        assertThat(query.whereClauses[1].second).isEqualTo(QueryOperator.LESS_THAN)
        assertThat(query.whereClauses[2].second).isEqualTo(QueryOperator.NOT_EQUAL)
    }

    @Test
    fun `SyncQuery listen returns a ListenerRegistration`() {
        val query = MockSyncQuery()

        val registration = query.listen { }

        assertThat(registration).isInstanceOf(ListenerRegistration::class.java)
    }

    @Test
    fun `SyncQuery callback get invokes onSuccess`() {
        val query = MockSyncQuery()
        var receivedDocs: List<SyncDocument>? = null

        query.get(
            onSuccess = { docs -> receivedDocs = docs },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(receivedDocs).isNotNull()
        assertThat(receivedDocs).isEmpty()
    }

    // ==================== SyncCollection Interface Tests ====================

    @Test
    fun `SyncCollection exposes id, name, and databaseId`() {
        val collection = MockSyncCollection(id = "col-abc", name = "products", databaseId = "db-xyz")

        assertThat(collection.id).isEqualTo("col-abc")
        assertThat(collection.name).isEqualTo("products")
        assertThat(collection.databaseId).isEqualTo("db-xyz")
    }

    @Test
    fun `SyncCollection implements the interface`() {
        val collection: SyncCollection = MockSyncCollection()

        assertThat(collection).isInstanceOf(SyncCollection::class.java)
    }

    @Test
    fun `SyncCollection query returns a SyncQuery`() {
        val collection = MockSyncCollection()

        val query = collection.query()

        assertThat(query).isInstanceOf(SyncQuery::class.java)
    }

    @Test
    fun `SyncCollection where returns a SyncQuery`() {
        val collection = MockSyncCollection()

        val query = collection.where("name", QueryOperator.EQUAL, "John")

        assertThat(query).isInstanceOf(SyncQuery::class.java)
    }

    @Test
    fun `SyncCollection listen returns a ListenerRegistration`() {
        val collection = MockSyncCollection()

        val registration = collection.listen { }

        assertThat(registration).isInstanceOf(ListenerRegistration::class.java)
    }

    @Test
    fun `SyncCollection listenDocument returns a ListenerRegistration`() {
        val collection = MockSyncCollection()

        val registration = collection.listenDocument("doc-1") { }

        assertThat(registration).isInstanceOf(ListenerRegistration::class.java)
    }

    @Test
    fun `SyncCollection callback add invokes onSuccess`() {
        val collection = MockSyncCollection()
        var result: SyncDocument? = null

        collection.add(
            data = mapOf("name" to "Test"),
            onSuccess = { doc -> result = doc },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(result).isNotNull()
        assertThat(result!!.data["name"]).isEqualTo("Test")
    }

    @Test
    fun `SyncCollection callback delete invokes onSuccess`() {
        val collection = MockSyncCollection()
        var successCalled = false

        collection.delete(
            documentId = "doc-to-delete",
            onSuccess = { successCalled = true },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(successCalled).isTrue()
        assertThat(collection.deletedIds).contains("doc-to-delete")
    }

    @Test
    fun `SyncCollection callback set invokes onSuccess`() {
        val collection = MockSyncCollection()
        var result: SyncDocument? = null

        collection.set(
            documentId = "doc-1",
            data = mapOf("field" to "value"),
            onSuccess = { doc -> result = doc },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo("doc-1")
    }

    @Test
    fun `SyncCollection callback update invokes onSuccess`() {
        val collection = MockSyncCollection()
        var result: SyncDocument? = null

        collection.update(
            documentId = "doc-1",
            data = mapOf("status" to "updated"),
            onSuccess = { doc -> result = doc },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(result).isNotNull()
        assertThat(result!!.data["status"]).isEqualTo("updated")
    }

    @Test
    fun `SyncCollection callback get invokes onSuccess`() {
        val collection = MockSyncCollection()
        var callbackInvoked = false

        collection.get(
            documentId = "any-doc",
            onSuccess = { callbackInvoked = true },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(callbackInvoked).isTrue()
    }

    @Test
    fun `SyncCollection callback getAll invokes onSuccess`() {
        val collection = MockSyncCollection()
        var receivedDocs: List<SyncDocument>? = null

        collection.getAll(
            onSuccess = { docs -> receivedDocs = docs },
            onError = { throw AssertionError("Should not fail") }
        )

        assertThat(receivedDocs).isNotNull()
        assertThat(receivedDocs).isEmpty()
    }

    // ==================== SyncDatabase Interface Tests ====================

    @Test
    fun `SyncDatabase exposes id and name`() {
        val database = MockSyncDatabase(id = "db-789", name = "test-db")

        assertThat(database.id).isEqualTo("db-789")
        assertThat(database.name).isEqualTo("test-db")
    }

    @Test
    fun `SyncDatabase implements the interface`() {
        val database: SyncDatabase = MockSyncDatabase()

        assertThat(database).isInstanceOf(SyncDatabase::class.java)
    }

    @Test
    fun `SyncDatabase collection returns a SyncCollection`() {
        val database = MockSyncDatabase()

        val collection = database.collection("users")

        assertThat(collection).isInstanceOf(SyncCollection::class.java)
    }

    @Test
    fun `SyncDatabase collection passes databaseId to collection`() {
        val database = MockSyncDatabase(id = "db-test")

        val collection = database.collection("orders")

        assertThat(collection.databaseId).isEqualTo("db-test")
    }

    @Test
    fun `SyncDatabase collection uses collectionIdOrName for id`() {
        val database = MockSyncDatabase()

        val collection = database.collection("products")

        assertThat(collection.id).isEqualTo("products")
    }

    // ==================== Integration-style Tests ====================

    @Test
    fun `Database to collection to query chain works`() {
        val database = MockSyncDatabase(id = "db-1", name = "main")
        val collection = database.collection("users")
        val query = collection.where("active", QueryOperator.EQUAL, true)

        assertThat(database).isInstanceOf(SyncDatabase::class.java)
        assertThat(collection).isInstanceOf(SyncCollection::class.java)
        assertThat(query).isInstanceOf(SyncQuery::class.java)
    }

    @Test
    fun `Collection listen and remove lifecycle`() {
        val collection = MockSyncCollection()

        val reg1 = collection.listen { }
        val reg2 = collection.listenDocument("doc-1") { }

        assertThat(collection.listeners).hasSize(2)

        reg1.remove()
        reg2.remove()

        // Both registrations should be MockListenerRegistration with removed = true
        assertThat((reg1 as MockListenerRegistration).removed).isTrue()
        assertThat((reg2 as MockListenerRegistration).removed).isTrue()
    }

    @Test
    fun `SyncQuery complex query with all operators`() {
        val query = MockSyncQuery()

        query
            .where("age", QueryOperator.GREATER_THAN, 18)
            .where("age", QueryOperator.LESS_THAN_OR_EQUAL, 65)
            .where("role", QueryOperator.IN, listOf("admin", "editor"))
            .where("banned", QueryOperator.NOT_IN, listOf(true))
            .where("tags", QueryOperator.ARRAY_CONTAINS, "verified")
            .orderBy("age", OrderDirection.DESCENDING)
            .orderBy("name", OrderDirection.ASCENDING)
            .limit(50)
            .offset(0)

        assertThat(query.whereClauses).hasSize(5)
        assertThat(query.orderByClauses).hasSize(2)
        assertThat(query.limitValue).isEqualTo(50)
        assertThat(query.offsetValue).isEqualTo(0)
    }

    @Test
    fun `QueryOperator each has a unique value`() {
        val values = QueryOperator.entries.map { it.value }

        assertThat(values).containsNoDuplicates()
    }

    @Test
    fun `OrderDirection each has a unique value`() {
        val values = OrderDirection.entries.map { it.value }

        assertThat(values).containsNoDuplicates()
    }
}
