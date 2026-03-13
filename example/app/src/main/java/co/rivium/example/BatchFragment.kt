package co.rivium.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import co.rivium.sync.sdk.SyncCollection
import co.rivium.sync.sdk.SyncDocument
import co.rivium.sync.sdk.RiviumSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchFragment : Fragment() {

    private lateinit var collection: SyncCollection
    private lateinit var resultText: TextView
    private var documents: List<SyncDocument> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_batch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = RiviumSync.getInstance().database(AppConfig.databaseId)
        collection = db.collection(AppConfig.TODOS_COLLECTION)

        resultText = view.findViewById(R.id.resultText)

        view.findViewById<MaterialButton>(R.id.batchCreateButton).setOnClickListener {
            batchCreate()
        }

        view.findViewById<MaterialButton>(R.id.batchUpdateButton).setOnClickListener {
            batchUpdate()
        }

        view.findViewById<MaterialButton>(R.id.batchDeleteButton).setOnClickListener {
            batchDeleteCompleted()
        }

        view.findViewById<MaterialButton>(R.id.batchMixedButton).setOnClickListener {
            batchMixed()
        }

        // Load documents for batch operations
        loadDocuments()
    }

    private fun loadDocuments() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                documents = collection.getAll()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun batchCreate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val batch = RiviumSync.getInstance().batch()
                val now = getCurrentISODate()

                batch.create(collection, mapOf(
                    "title" to "Batch Task 1",
                    "completed" to false,
                    "createdAt" to now
                ))
                batch.create(collection, mapOf(
                    "title" to "Batch Task 2",
                    "completed" to false,
                    "createdAt" to now
                ))
                batch.create(collection, mapOf(
                    "title" to "Batch Task 3",
                    "completed" to false,
                    "createdAt" to now
                ))

                batch.commit()

                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_CREATE", mapOf(
                        "success" to true,
                        "documentsCreated" to 3
                    )))
                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_CREATE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun batchUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh documents first
                documents = collection.getAll()

                if (documents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        setResult("No documents to update")
                    }
                    return@launch
                }

                val batch = RiviumSync.getInstance().batch()
                val now = getCurrentISODate()

                documents.forEach { doc ->
                    batch.update(collection, doc.id, mapOf(
                        "batchUpdatedAt" to now
                    ))
                }

                batch.commit()

                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_UPDATE", mapOf(
                        "success" to true,
                        "documentsUpdated" to documents.size
                    )))
                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_UPDATE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun batchDeleteCompleted() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh documents first
                documents = collection.getAll()

                val completedDocs = documents.filter { it.data["completed"] == true }

                if (completedDocs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        setResult("No completed documents to delete")
                    }
                    return@launch
                }

                val batch = RiviumSync.getInstance().batch()

                completedDocs.forEach { doc ->
                    batch.delete(collection, doc.id)
                }

                batch.commit()

                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_DELETE", mapOf(
                        "success" to true,
                        "documentsDeleted" to completedDocs.size
                    )))
                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_DELETE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun batchMixed() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh documents first
                documents = collection.getAll()

                val batch = RiviumSync.getInstance().batch()
                val now = getCurrentISODate()

                var created = 0
                var updated = 0
                var deleted = 0

                // Create one
                batch.create(collection, mapOf(
                    "title" to "Mixed: New Task",
                    "completed" to false,
                    "createdAt" to now
                ))
                created = 1

                // Update first if exists
                if (documents.isNotEmpty()) {
                    batch.update(collection, documents[0].id, mapOf(
                        "mixedUpdatedAt" to now
                    ))
                    updated = 1
                }

                // Delete last completed if exists
                val lastCompleted = documents.lastOrNull { it.data["completed"] == true }
                if (lastCompleted != null) {
                    batch.delete(collection, lastCompleted.id)
                    deleted = 1
                }

                batch.commit()

                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_MIXED", mapOf(
                        "success" to true,
                        "operations" to mapOf(
                            "created" to created,
                            "updated" to updated,
                            "deleted" to deleted
                        )
                    )))
                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildResult("BATCH_MIXED", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun setResult(text: String) {
        resultText.text = text
    }

    private fun buildResult(operation: String, data: Map<String, Any?>): String {
        val json = JSONObject()
        json.put("operation", operation)
        data.forEach { (key, value) ->
            if (value is Map<*, *>) {
                val nested = JSONObject()
                value.forEach { (k, v) ->
                    nested.put(k.toString(), v)
                }
                json.put(key, nested)
            } else {
                json.put(key, value)
            }
        }
        return json.toString(2)
    }

    private fun getCurrentISODate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }
}
