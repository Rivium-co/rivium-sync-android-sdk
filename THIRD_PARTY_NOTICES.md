# Third-Party Notices

`rivium-sync-android` uses the following third-party components.
None of them is vendored: they are Gradle dependencies that your build pulls
from Maven Central / Google Maven. See each upstream repository for the full
license text.

---

## Eclipse Paho MQTT client (via `co.rivium:pn-protocol`)

Used for the realtime connection (live listeners). Rivium's `pn-protocol`
library depends on it.

- Homepage: https://github.com/eclipse/paho.mqtt.java
- Version: 1.2.5
- License: Eclipse Public License 2.0 / Eclipse Distribution License 1.0 (dual)

---

## OkHttp

HTTP client for the Sync API.

- Homepage: https://github.com/square/okhttp
- Version: 4.12.0
- License: Apache License 2.0

---

## Gson

JSON encoding and decoding of documents.

- Homepage: https://github.com/google/gson
- Version: 2.10.1
- License: Apache License 2.0

---

## Kotlin coroutines

- Homepage: https://github.com/Kotlin/kotlinx.coroutines
- Version: 1.7.3
- License: Apache License 2.0

---

## AndroidX Core and Room

Room stores the offline cache and the queue of pending writes.

- Homepage: https://developer.android.com/jetpack/androidx
- Versions: core-ktx 1.12.0, Room 2.6.1
- License: Apache License 2.0
