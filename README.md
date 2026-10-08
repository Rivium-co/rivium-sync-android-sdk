# RiviumSync Android SDK

Realtime database SDK for Android with offline-first sync.

[![Maven Central](https://img.shields.io/maven-central/v/co.rivium/rivium-sync-android)](https://central.sonatype.com/artifact/co.rivium/rivium-sync-android)
[![API 21+](https://img.shields.io/badge/API-21+-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("co.rivium:rivium-sync-android:0.2.2")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'co.rivium:rivium-sync-android:0.2.2'
}
```

## Usage

Databases and collections are addressed by their **name**, exactly as shown in
Rivium Console:

```kotlin
val sync = RiviumSync.initialize(
    applicationContext,
    RiviumSyncConfig.builder("rv_live_your_api_key").build()
)
// Inside a coroutine (connect, add and friends are suspend functions):
sync.connect()

val todos = sync.database("my-app").collection("todos")

todos.add(mapOf("title" to "Buy milk", "completed" to false))

val registration = todos.listen { documents ->
    println("Todos updated: ${documents.size}")
}
```

Use names, not UUIDs. Realtime updates are published per database and
collection name, so a UUID still works for plain reads and writes but live
updates never arrive.

## Verified user identity

Security Rules check `auth.uid`. The API key ships inside your app, so the app
cannot be trusted to say who the user is - only your own server can. Have your
backend mint a short-lived user token and give the SDK a provider:

```kotlin
val config = RiviumSyncConfig.builder("rv_live_your_api_key")
    .userTokenProvider { myBackend.fetchSyncToken() } // called off the main thread
    .build()
```

The SDK asks for a token when it needs one and again before the old one
expires. Your backend mints it with your project's server secret, which must
stay on your server and never ship in an app.

If your project has **Require signed user tokens** turned on in the Console, a
token provider is required; without it, requests are refused.

You can call `connect()` before anyone is signed in. With tokens required, the
SDK then waits (`isAwaitingUserToken`, `onAwaitingUserToken`) instead of
failing, and connects by itself once it has a token: after
`userTokens.set(token)`, or after `userTokens.refresh()` when you use a
provider. Call the same when the user changes; the SDK reconnects as the new
user.

See `example/` for a working setup.

## Documentation

- [Rivium Cloud](https://rivium.co/cloud)
- [Rivium Console](https://console.rivium.co)

## License

MIT
