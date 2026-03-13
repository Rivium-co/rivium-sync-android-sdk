package co.rivium.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import co.rivium.sync.sdk.ListenerRegistration
import co.rivium.sync.sdk.SyncCollection
import co.rivium.sync.sdk.SyncDocument
import co.rivium.sync.sdk.RiviumSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RealtimeFragment : Fragment() {

    private lateinit var collection: SyncCollection
    private var listenerRegistration: ListenerRegistration? = null
    private var documents: List<SyncDocument> = emptyList()

    private lateinit var eventLogContainer: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_realtime, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = RiviumSync.getInstance().database(AppConfig.databaseId)
        collection = db.collection(AppConfig.TODOS_COLLECTION)

        eventLogContainer = view.findViewById(R.id.eventLogContainer)
        statusDot = view.findViewById(R.id.statusDot)
        statusText = view.findViewById(R.id.statusText)
        startButton = view.findViewById(R.id.startListenerButton)
        stopButton = view.findViewById(R.id.stopListenerButton)

        startButton.setOnClickListener { startListener() }
        stopButton.setOnClickListener { stopListener() }

        view.findViewById<MaterialButton>(R.id.createTestButton).setOnClickListener {
            createTestDocument()
        }

        view.findViewById<MaterialButton>(R.id.updateTestButton).setOnClickListener {
            updateRandomDocument()
        }

        view.findViewById<MaterialButton>(R.id.deleteTestButton).setOnClickListener {
            deleteRandomDocument()
        }

        // Load initial documents
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

    private fun startListener() {
        if (listenerRegistration != null) return

        listenerRegistration = collection.listen { docs ->
            val prevIds = documents.map { it.id }.toSet()
            val newIds = docs.map { it.id }.toSet()

            // Detect new documents
            docs.filter { it.id !in prevIds }.forEach { doc ->
                addEventLog("create", "Created: ${doc.data["title"] ?: doc.id}")
            }

            // Detect deleted documents
            documents.filter { it.id !in newIds }.forEach { doc ->
                addEventLog("delete", "Deleted: ${doc.data["title"] ?: doc.id}")
            }

            documents = docs
            addEventLog("info", "Collection updated: ${docs.size} documents")
        }

        updateListenerUI(true)
        addEventLog("info", "Listener started")
    }

    private fun stopListener() {
        listenerRegistration?.remove()
        listenerRegistration = null

        updateListenerUI(false)
        addEventLog("info", "Listener stopped")
    }

    private fun updateListenerUI(active: Boolean) {
        if (active) {
            statusDot.setBackgroundResource(R.drawable.status_dot_active)
            statusText.text = "Active"
            startButton.isEnabled = false
            stopButton.isEnabled = true
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_inactive)
            statusText.text = "Inactive"
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun addEventLog(type: String, message: String) {
        activity?.runOnUiThread {
            // Remove empty state if present
            val emptyState = eventLogContainer.findViewById<TextView>(R.id.emptyState)
            emptyState?.visibility = View.GONE

            val eventView = TextView(requireContext()).apply {
                text = "[${getCurrentTime()}] $message"
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(16, 12, 16, 12)

                val bgColor = when (type) {
                    "create" -> 0x3310B981.toInt()
                    "update" -> 0x33F59E0B.toInt()
                    "delete" -> 0x33EF4444.toInt()
                    else -> 0x3360A5FA.toInt()
                }
                setBackgroundColor(bgColor)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 8
                layoutParams = params
            }

            eventLogContainer.addView(eventView, 0)

            // Keep only last 50 events
            while (eventLogContainer.childCount > 50) {
                eventLogContainer.removeViewAt(eventLogContainer.childCount - 1)
            }
        }
    }

    private fun createTestDocument() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                collection.add(mapOf(
                    "title" to "Realtime Test ${System.currentTimeMillis()}",
                    "completed" to false,
                    "createdAt" to getCurrentISODate()
                ))
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addEventLog("info", "Error: ${e.message}")
                }
            }
        }
    }

    private fun updateRandomDocument() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh documents
                documents = collection.getAll()

                if (documents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        addEventLog("info", "No documents to update")
                    }
                    return@launch
                }

                val randomDoc = documents.random()
                collection.update(randomDoc.id, mapOf(
                    "realtimeUpdatedAt" to getCurrentISODate()
                ))

                withContext(Dispatchers.Main) {
                    addEventLog("update", "Updated: ${randomDoc.data["title"] ?: randomDoc.id}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addEventLog("info", "Error: ${e.message}")
                }
            }
        }
    }

    private fun deleteRandomDocument() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh documents
                documents = collection.getAll()

                if (documents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        addEventLog("info", "No documents to delete")
                    }
                    return@launch
                }

                val randomDoc = documents.random()
                collection.delete(randomDoc.id)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addEventLog("info", "Error: ${e.message}")
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    private fun getCurrentISODate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
