# RiviumSync Android SDK

Realtime database SDK for Android with offline-first sync powered by pn-protocol.

[![Maven Central](https://img.shields.io/maven-central/v/co.rivium/rivium-sync-android)](https://central.sonatype.com/artifact/co.rivium/rivium-sync-android)
[![API 21+](https://img.shields.io/badge/API-21+-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("co.rivium:rivium-sync-android:0.2.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'co.rivium:rivium-sync-android:0.2.0'
}
```

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

See `example/` for a working setup.

## Documentation

- [Rivium Cloud](https://rivium.co/cloud)
- [Rivium Console](https://console.rivium.co)

## License

MIT
