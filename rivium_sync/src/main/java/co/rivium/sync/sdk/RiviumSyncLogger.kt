package co.rivium.sync.sdk

import android.util.Log

/**
 * Internal logger for RiviumSync SDK
 * All logs are only printed when debugMode is enabled to avoid exposing internal implementation details
 */
internal object RiviumSyncLogger {
    private const val TAG = "RiviumSync"
    var debugMode: Boolean = false

    fun d(message: String) {
        if (debugMode) {
            Log.d(TAG, message)
        }
    }

    fun i(message: String) {
        if (debugMode) {
            Log.i(TAG, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (debugMode) {
            if (throwable != null) {
                Log.w(TAG, message, throwable)
            } else {
                Log.w(TAG, message)
            }
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (debugMode) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }
}
