package co.rivium.example

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayoutMediator
import co.rivium.example.databinding.ActivityMainBinding
import co.rivium.sync.sdk.RiviumSync
import co.rivium.sync.sdk.RiviumSyncConfig

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connectionDot: View
    private lateinit var connectionStatusText: TextView
    private lateinit var connectButton: MaterialButton

    private var isConnected = false
    private var isConnecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionDot = binding.root.findViewById(R.id.connectionDot)
        connectionStatusText = binding.root.findViewById(R.id.connectionStatusText)
        connectButton = binding.root.findViewById(R.id.connectButton)

        connectButton.setOnClickListener { toggleConnection() }

        initializeRiviumSync()
        setupViewPager()
    }

    private fun initializeRiviumSync() {
        try {
            val config = RiviumSyncConfig.builder(AppConfig.apiKey)
                .offlineEnabled(true)
                .debugMode(true)
                .build()

            RiviumSync.initialize(applicationContext, config)
            Log.d(TAG, "RiviumSync SDK initialized")

            // Set connection listener for real-time status updates
            RiviumSync.getInstance().setConnectionListener(object : RiviumSync.ConnectionListener {
                override fun onConnected() {
                    runOnUiThread {
                        isConnected = true
                        isConnecting = false
                        updateConnectionUI()
                    }
                }

                override fun onDisconnected(cause: Throwable?) {
                    runOnUiThread {
                        isConnected = false
                        isConnecting = false
                        updateConnectionUI()
                    }
                }

                override fun onConnectionFailed(cause: Throwable) {
                    runOnUiThread {
                        isConnected = false
                        isConnecting = false
                        updateConnectionUI()
                    }
                }
            })

            // Auto-connect to realtime sync service
            isConnecting = true
            updateConnectionUI()
            RiviumSync.getInstance().connect(
                onSuccess = { Log.d(TAG, "RiviumSync connected") },
                onError = { error -> Log.e(TAG, "RiviumSync connection failed", error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RiviumSync", e)
        }
    }

    private fun toggleConnection() {
        if (isConnecting) return

        isConnecting = true
        updateConnectionUI()

        if (isConnected) {
            RiviumSync.getInstance().disconnect()
        } else {
            RiviumSync.getInstance().connect(
                onSuccess = { Log.d(TAG, "RiviumSync reconnected") },
                onError = { error -> Log.e(TAG, "RiviumSync reconnection failed", error) }
            )
        }
    }

    private fun updateConnectionUI() {
        val dotColor = when {
            isConnecting -> ContextCompat.getColor(this, R.color.orange_500)
            isConnected -> ContextCompat.getColor(this, R.color.emerald_200)
            else -> ContextCompat.getColor(this, R.color.red_500)
        }
        connectionDot.backgroundTintList = ColorStateList.valueOf(dotColor)

        connectionStatusText.text = when {
            isConnecting -> "Connecting..."
            isConnected -> "Connected"
            else -> "Disconnected"
        }

        connectButton.text = when {
            isConnecting -> "..."
            isConnected -> "Disconnect"
            else -> "Connect"
        }
        connectButton.isEnabled = !isConnecting
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_crud)
                1 -> getString(R.string.tab_query)
                2 -> getString(R.string.tab_batch)
                3 -> getString(R.string.tab_realtime)
                4 -> getString(R.string.tab_offline)
                else -> ""
            }
        }.attach()
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CrudFragment()
                1 -> QueryFragment()
                2 -> BatchFragment()
                3 -> RealtimeFragment()
                4 -> OfflineFragment()
                else -> CrudFragment()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
