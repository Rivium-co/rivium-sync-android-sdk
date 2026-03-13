package co.rivium.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import co.rivium.sync.sdk.SyncCollection
import co.rivium.sync.sdk.RiviumSync
import co.rivium.sync.sdk.offline.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineFragment : Fragment() {

    private lateinit var collection: SyncCollection

    private lateinit var connectionStatusText: TextView
    private lateinit var connectionStatusIcon: View
    private lateinit var syncStateText: TextView
    private lateinit var syncStateLabel: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var offlineEnabledChip: TextView
    private lateinit var resultText: TextView
    private lateinit var forceSyncButton: MaterialButton

    private var isConnected = false
    private var syncState: SyncState = SyncState.IDLE
    private var pendingCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_offline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = RiviumSync.getInstance().database(AppConfig.databaseId)
        collection = db.collection(AppConfig.TODOS_COLLECTION)

        // Initialize views
        connectionStatusText = view.findViewById(R.id.connectionStatusText)
        connectionStatusIcon = view.findViewById(R.id.connectionStatusIcon)
        syncStateText = view.findViewById(R.id.syncStateText)
        syncStateLabel = view.findViewById(R.id.syncStateLabel)
        pendingCountText = view.findViewById(R.id.pendingCountText)
        offlineEnabledChip = view.findViewById(R.id.offlineEnabledChip)
        resultText = view.findViewById(R.id.resultText)
        forceSyncButton = view.findViewById(R.id.forceSyncButton)

        // Setup buttons
        view.findViewById<MaterialButton>(R.id.createDocumentButton).setOnClickListener {
            createDocument()
        }

        view.findViewById<MaterialButton>(R.id.readDocumentsButton).setOnClickListener {
            readDocuments()
        }

        forceSyncButton.setOnClickListener {
            forceSyncNow()
        }

        view.findViewById<MaterialButton>(R.id.clearCacheButton).setOnClickListener {
            clearLocalCache()
        }

        // Setup StateFlow observers for offline state
        setupStateFlowObservers()

        // Check initial status
        checkStatus()
    }

    private fun setupStateFlowObservers() {
        // Observe sync state - collect on Main dispatcher for UI updates
        RiviumSync.getInstance().getSyncState()?.let { stateFlow ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                stateFlow.collectLatest { state ->
                    syncState = state
                    updateSyncStateUI()
                }
            }
        }

        // Observe pending count - collect on Main dispatcher for UI updates
        RiviumSync.getInstance().getPendingCount()?.let { countFlow ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                countFlow.collectLatest { count ->
                    pendingCount = count
                    updatePendingCountUI()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh status when fragment becomes visible
        checkStatus()
    }

    private fun checkStatus() {
        isConnected = RiviumSync.getInstance().isConnected()
        syncState = RiviumSync.getInstance().getSyncState()?.value ?: SyncState.IDLE
        pendingCount = RiviumSync.getInstance().getPendingCount()?.value ?: 0

        updateConnectionUI()
        updateSyncStateUI()
        updatePendingCountUI()
        updateOfflineEnabledUI()
    }

    private fun updateConnectionUI() {
        connectionStatusText.text = if (isConnected) "Connected" else "Disconnected"
        val iconColor = if (isConnected) {
            ContextCompat.getColor(requireContext(), R.color.emerald_500)
        } else {
            ContextCompat.getColor(requireContext(), R.color.orange_500)
        }
        connectionStatusIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(iconColor)
    }

    private fun updateSyncStateUI() {
        val stateString = syncStateToString(syncState)
        syncStateText.text = stateString
        syncStateLabel.text = "Sync state: $stateString"
    }

    private fun updatePendingCountUI() {
        pendingCountText.text = pendingCount.toString()
        forceSyncButton.isEnabled = pendingCount > 0
    }

    private fun updateOfflineEnabledUI() {
        offlineEnabledChip.text = if (RiviumSync.getInstance().isOfflineEnabled()) {
            "Offline enabled"
        } else {
            "Online only"
        }
    }

    private fun syncStateToString(state: SyncState): String {
        return when (state) {
            SyncState.IDLE -> "Idle"
            SyncState.SYNCING -> "Syncing..."
            SyncState.OFFLINE -> "Offline"
            SyncState.ERROR -> "Error"
        }
    }

    private fun createDocument() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Refresh connection status before creating
                val currentlyConnected = RiviumSync.getInstance().isConnected()

                val doc = collection.add(mapOf(
                    "title" to "Offline Test ${System.currentTimeMillis()}",
                    "timestamp" to getCurrentISODate(),
                    "isConnected" to currentlyConnected
                ))

                withContext(Dispatchers.Main) {
                    isConnected = currentlyConnected
                    val statusMsg = if (currentlyConnected) {
                        "Online - synced immediately"
                    } else {
                        "Offline - queued for sync"
                    }
                    setResult("Document created: ${doc.id}\nConnection: $statusMsg")
                    checkStatus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult("Error creating document: ${e.message}")
                }
            }
        }
    }

    private fun readDocuments() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val docs = collection.getAll()

                withContext(Dispatchers.Main) {
                    if (docs.isEmpty()) {
                        setResult("No documents found")
                    } else {
                        val preview = docs.take(3).joinToString("\n") { doc ->
                            "- ${doc.id.take(8)}...: ${doc.data["title"] ?: "no title"}"
                        }
                        val more = if (docs.size > 3) "\n... and ${docs.size - 3} more" else ""
                        setResult("Read ${docs.size} documents:\n$preview$more")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult("Error reading documents: ${e.message}")
                }
            }
        }
    }

    private fun forceSyncNow() {
        RiviumSync.getInstance().forceSyncNow()
        setResult("Force sync triggered")
        // Refresh status after triggering sync
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Small delay to let sync start
            checkStatus()
        }
    }

    private fun clearLocalCache() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RiviumSync.getInstance().clearOfflineCache()

                withContext(Dispatchers.Main) {
                    setResult("Local cache cleared")
                    checkStatus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult("Error clearing cache: ${e.message}")
                }
            }
        }
    }

    private fun setResult(text: String) {
        resultText.text = text
    }

    private fun getCurrentISODate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }
}
