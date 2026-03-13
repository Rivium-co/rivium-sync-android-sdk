package co.rivium.sync.sdk

import com.google.common.truth.Truth.assertThat
import co.rivium.sync.sdk.QueryOperator
import co.rivium.sync.sdk.OrderDirection
import org.junit.Test

/**
 * Tests for Query-related enums and interfaces
 */
class QueryTest {

    @Test
    fun `QueryOperator has all expected values`() {
        val operators = QueryOperator.entries

        assertThat(operators).hasSize(9)
        assertThat(operators).containsExactly(
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
    fun `QueryOperator has correct string values`() {
        assertThat(QueryOperator.EQUAL.value).isEqualTo("==")
        assertThat(QueryOperator.NOT_EQUAL.value).isEqualTo("!=")
        assertThat(QueryOperator.GREATER_THAN.value).isEqualTo(">")
        assertThat(QueryOperator.GREATER_THAN_OR_EQUAL.value).isEqualTo(">=")
        assertThat(QueryOperator.LESS_THAN.value).isEqualTo("<")
        assertThat(QueryOperator.LESS_THAN_OR_EQUAL.value).isEqualTo("<=")
        assertThat(QueryOperator.ARRAY_CONTAINS.value).isEqualTo("array-contains")
        assertThat(QueryOperator.IN.value).isEqualTo("in")
        assertThat(QueryOperator.NOT_IN.value).isEqualTo("not-in")
    }

    @Test
    fun `OrderDirection has correct values`() {
        val directions = OrderDirection.entries

        assertThat(directions).hasSize(2)
        assertThat(OrderDirection.ASCENDING.value).isEqualTo("asc")
        assertThat(OrderDirection.DESCENDING.value).isEqualTo("desc")
    }
}
