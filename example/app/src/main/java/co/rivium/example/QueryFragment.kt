package co.rivium.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import co.rivium.sync.sdk.QueryOperator
import co.rivium.sync.sdk.SyncCollection
import co.rivium.sync.sdk.RiviumSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class QueryFragment : Fragment() {

    private lateinit var collection: SyncCollection
    private lateinit var resultText: TextView
    private lateinit var fieldInput: TextInputEditText
    private lateinit var operatorSpinner: MaterialAutoCompleteTextView
    private lateinit var valueInput: TextInputEditText
    private lateinit var orderByInput: TextInputEditText
    private lateinit var limitInput: TextInputEditText

    private val operators = listOf("==", "!=", ">", ">=", "<", "<=")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_query, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = RiviumSync.getInstance().database(AppConfig.databaseId)
        collection = db.collection(AppConfig.TODOS_COLLECTION)

        fieldInput = view.findViewById(R.id.fieldInput)
        operatorSpinner = view.findViewById(R.id.operatorSpinner)
        valueInput = view.findViewById(R.id.valueInput)
        orderByInput = view.findViewById(R.id.orderByInput)
        limitInput = view.findViewById(R.id.limitInput)
        resultText = view.findViewById(R.id.resultText)

        val runQueryButton = view.findViewById<MaterialButton>(R.id.runQueryButton)

        // Setup operator dropdown
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, operators)
        operatorSpinner.setAdapter(adapter)
        operatorSpinner.setText("==", false)

        // Set default values
        fieldInput.setText("completed")
        valueInput.setText("false")
        limitInput.setText("10")

        runQueryButton.setOnClickListener {
            runQuery()
        }
    }

    private fun runQuery() {
        val field = fieldInput.text?.toString()?.trim() ?: ""
        val operatorStr = operatorSpinner.text?.toString() ?: "=="
        val valueStr = valueInput.text?.toString()?.trim() ?: ""
        val orderBy = orderByInput.text?.toString()?.trim()
        val limit = limitInput.text?.toString()?.toIntOrNull() ?: 10

        // Parse value
        val value: Any = when {
            valueStr == "true" -> true
            valueStr == "false" -> false
            valueStr.toDoubleOrNull() != null -> valueStr.toDouble()
            else -> valueStr
        }

        // Map operator string to enum
        val operator = when (operatorStr) {
            "==" -> QueryOperator.EQUAL
            "!=" -> QueryOperator.NOT_EQUAL
            ">" -> QueryOperator.GREATER_THAN
            ">=" -> QueryOperator.GREATER_THAN_OR_EQUAL
            "<" -> QueryOperator.LESS_THAN
            "<=" -> QueryOperator.LESS_THAN_OR_EQUAL
            else -> QueryOperator.EQUAL
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Build query using the fluent API
                var query = collection.query()

                if (field.isNotEmpty()) {
                    query = query.where(field, operator, value)
                }

                if (!orderBy.isNullOrEmpty()) {
                    query = query.orderBy(orderBy)
                }

                query = query.limit(limit)

                val results = query.get()

                val json = JSONObject()
                json.put("query", JSONObject().apply {
                    put("field", field)
                    put("operator", operatorStr)
                    put("value", value)
                    put("orderBy", orderBy)
                    put("limit", limit)
                })
                json.put("resultsCount", results.size)

                val resultsArray = JSONArray()
                results.forEach { doc ->
                    val docJson = JSONObject()
                    docJson.put("id", doc.id)
                    doc.data.forEach { (key, v) ->
                        when (v) {
                            is Boolean -> docJson.put(key, v)
                            is Number -> docJson.put(key, v)
                            is String -> docJson.put(key, v)
                            null -> docJson.put(key, JSONObject.NULL)
                            else -> docJson.put(key, v.toString())
                        }
                    }
                    resultsArray.put(docJson)
                }
                json.put("results", resultsArray)

                withContext(Dispatchers.Main) {
                    resultText.text = json.toString(2)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val json = JSONObject()
                    json.put("error", e.message)
                    resultText.text = json.toString(2)
                }
            }
        }
    }
}
