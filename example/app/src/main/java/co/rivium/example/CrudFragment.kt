package co.rivium.example

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import co.rivium.example.databinding.FragmentCrudBinding
import co.rivium.example.databinding.ItemDocumentBinding
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

class CrudFragment : Fragment() {

    private var _binding: FragmentCrudBinding? = null
    private val binding get() = _binding!!

    private lateinit var collection: SyncCollection
    private val adapter = DocumentAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = RiviumSync.getInstance().database(AppConfig.databaseId)
        collection = db.collection(AppConfig.TODOS_COLLECTION)

        setupRecyclerView()
        setupClickListeners()
        loadDocuments()
    }

    private fun setupRecyclerView() {
        binding.documentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.documentsRecyclerView.adapter = adapter

        adapter.onCheckboxClick = { doc, isChecked ->
            toggleComplete(doc.id, isChecked)
        }

        adapter.onMenuClick = { doc, view ->
            showPopupMenu(doc, view)
        }
    }

    private fun setupClickListeners() {
        binding.createButton.setOnClickListener {
            createDocument()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadDocuments()
        }
    }

    private fun loadDocuments() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docs = collection.getAll()

                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.loadingIndicator.visibility = View.GONE

                    adapter.submitList(docs)
                    binding.documentsCount.text = "${docs.size} items"

                    if (docs.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                    }

                    setResult("Loaded ${docs.size} documents")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.loadingIndicator.visibility = View.GONE
                    setResult("Error: ${e.message}")
                }
            }
        }
    }

    private fun createDocument() {
        val title = binding.titleInput.text?.toString()?.trim()
        val description = binding.descriptionInput.text?.toString()?.trim()

        if (title.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = mapOf(
                    "title" to title,
                    "description" to (description ?: ""),
                    "completed" to false,
                    "createdAt" to getCurrentISODate()
                )

                val doc = collection.add(data)

                withContext(Dispatchers.Main) {
                    binding.titleInput.text?.clear()
                    binding.descriptionInput.text?.clear()

                    setResult(buildJsonResult("CREATE", mapOf(
                        "success" to true,
                        "id" to doc.id
                    )))

                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("CREATE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun readDocument(docId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = collection.get(docId)

                withContext(Dispatchers.Main) {
                    if (doc != null) {
                        setResult(buildJsonResult("READ", mapOf(
                            "id" to doc.id,
                            "data" to doc.data,
                            "version" to doc.version,
                            "createdAt" to doc.createdAt,
                            "updatedAt" to doc.updatedAt
                        )))
                    } else {
                        setResult(buildJsonResult("READ", mapOf(
                            "error" to "Document not found"
                        )))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("READ", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun updateDocument(docId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = mapOf(
                    "updatedAt" to getCurrentISODate(),
                    "description" to "Updated at ${getCurrentLocalDate()}"
                )

                val doc = collection.update(docId, data)

                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("UPDATE", mapOf(
                        "success" to true,
                        "id" to doc.id,
                        "version" to doc.version
                    )))

                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("UPDATE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun deleteDocument(docId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Are you sure you want to delete this document?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                performDelete(docId)
            }
            .show()
    }

    private fun performDelete(docId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                collection.delete(docId)

                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("DELETE", mapOf(
                        "success" to true,
                        "deletedId" to docId
                    )))

                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(buildJsonResult("DELETE", mapOf(
                        "error" to e.message
                    )))
                }
            }
        }
    }

    private fun toggleComplete(docId: String, completed: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = mutableMapOf<String, Any?>(
                    "completed" to completed
                )

                if (completed) {
                    data["completedAt"] = getCurrentISODate()
                } else {
                    data["completedAt"] = null
                }

                collection.update(docId, data)

                withContext(Dispatchers.Main) {
                    setResult("Toggled completed: $completed")
                    loadDocuments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult("Error: ${e.message}")
                }
            }
        }
    }

    private fun showPopupMenu(doc: SyncDocument, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.document_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_read -> {
                    readDocument(doc.id)
                    true
                }
                R.id.action_update -> {
                    updateDocument(doc.id)
                    true
                }
                R.id.action_delete -> {
                    deleteDocument(doc.id)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun setResult(text: String) {
        binding.resultText.text = text
    }

    private fun buildJsonResult(operation: String, data: Map<String, Any?>): String {
        val json = JSONObject()
        json.put("operation", operation)
        data.forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString(2)
    }

    private fun getCurrentISODate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }

    private fun getCurrentLocalDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Document Adapter
class DocumentAdapter : ListAdapter<SyncDocument, DocumentAdapter.ViewHolder>(DiffCallback()) {

    var onCheckboxClick: ((SyncDocument, Boolean) -> Unit)? = null
    var onMenuClick: ((SyncDocument, View) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(doc: SyncDocument) {
            val title = doc.data["title"]?.toString() ?: "Untitled"
            val description = doc.data["description"]?.toString() ?: ""
            val completed = doc.data["completed"] as? Boolean ?: false

            binding.titleText.text = title
            binding.descriptionText.text = description
            binding.checkbox.isChecked = completed

            if (completed) {
                binding.titleText.paintFlags = binding.titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.titleText.paintFlags = binding.titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            binding.checkbox.setOnClickListener {
                onCheckboxClick?.invoke(doc, binding.checkbox.isChecked)
            }

            binding.menuButton.setOnClickListener {
                onMenuClick?.invoke(doc, it)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SyncDocument>() {
        override fun areItemsTheSame(oldItem: SyncDocument, newItem: SyncDocument): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SyncDocument, newItem: SyncDocument): Boolean {
            return oldItem.data == newItem.data
        }
    }
}
