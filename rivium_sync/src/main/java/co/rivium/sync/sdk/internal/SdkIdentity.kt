package co.rivium.sync.sdk.internal

import co.rivium.sync.sdk.BuildConfig
import co.rivium.sync.sdk.RiviumSyncConfig
import co.rivium.sync.sdk.RiviumSyncLogger

/**
 * Identity this SDK reports to the backend: `X-Rivium-SDK: <name>/<version>`
 * on every API request (MQTT has no headers).
 *
 * Official wrappers (Flutter, React Native) set [RiviumSyncConfig.wrapperSdkName]
 * and [RiviumSyncConfig.wrapperSdkVersion]; when both are valid they replace the
 * native identity so the dashboard shows the SDK the app developer actually uses.
 * The header then also names this native SDK underneath, User-Agent style:
 * `X-Rivium-SDK: flutter/0.2.4 android/0.2.4` (wrapper first). Only public SDK
 * names and versions - never a key, user or device detail.
 */
internal data class SdkIdentity(val name: String, val version: String) {

    /** Value for the `X-Rivium-SDK` header: this identity, then the native SDK when this is a wrapper. */
    val headerValue: String
        get() = if (name == NATIVE_NAME) "$name/$version" else "$name/$version $NATIVE_NAME/${BuildConfig.SDK_VERSION}"

    companion object {
        const val NATIVE_NAME = "android"
        const val HEADER = "X-Rivium-SDK"

        /** Backend contract: <=32 chars, [A-Za-z0-9._+-]. */
        private val VALID = Regex("^[A-Za-z0-9._+-]{1,32}$")

        val native = SdkIdentity(NATIVE_NAME, BuildConfig.SDK_VERSION)

        internal fun isValid(value: String?): Boolean = value != null && VALID.matches(value)

        fun resolve(wrapperName: String?, wrapperVersion: String?): SdkIdentity {
            if (wrapperName == null && wrapperVersion == null) return native
            if (isValid(wrapperName) && isValid(wrapperVersion)) {
                return SdkIdentity(wrapperName!!, wrapperVersion!!)
            }
            RiviumSyncLogger.w("Ignoring invalid wrapper SDK identity '$wrapperName/$wrapperVersion'")
            return native
        }

        fun from(config: RiviumSyncConfig): SdkIdentity =
            resolve(config.wrapperSdkName, config.wrapperSdkVersion)
    }
}
