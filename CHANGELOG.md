# Changelog

## 0.2.6

- Realtime is always encrypted. If the Sync API's answer is missing or not an
  encrypted rivium.co endpoint, the SDK uses its built-in encrypted endpoint;
  it never connects unencrypted.

## 0.2.5

- Realtime connects where the Sync API tells it (host, port and whether the
  connection is encrypted), so your project can move to encrypted realtime
  (`mqtts`, port 8885) without an app update. Only a rivium.co endpoint is
  accepted, otherwise the SDK uses its built-in one; it never falls back from
  an encrypted connection to a plain one.
- Added `THIRD_PARTY_NOTICES.md`.

## 0.2.4

- Every request to the Sync API now carries `X-Rivium-SDK: android/<version>`,
  so the Console can show which SDK and version a project uses. Realtime
  (MQTT) is unchanged.
- New optional `RiviumSyncConfig.Builder.wrapperSdk(name, version)` for
  Rivium's Flutter and React Native wrappers: the header then reads
  `flutter/<version> android/<version>`. Apps don't need it.
- Added `RiviumSync.SDK_VERSION`; `RiviumSync.VERSION` now returns the real
  SDK version instead of "1.0.0".

## 0.2.3

- With offline persistence on, writes made before the realtime connection is up
  (or without calling `connect()`) now go to the server instead of waiting in
  the local queue. They are queued only when the device is known to be offline.

## 0.2.2

- Fixed a crash when the connection was closed while it was still opening,
  for example on a device with no internet.

## 0.2.1

- `connect()` can be called before sign-in when user tokens are required: the
  SDK waits for a token and then connects.
- Setting a token for a different user reconnects as that user.
- Added `userTokens.refresh()`, `isAwaitingUserToken` and `onAwaitingUserToken`.
- Listeners added before the SDK is connected now receive updates.

## 0.2.0

- Added `userToken` and `userTokenProvider`, so Security Rules can trust
  `auth.uid`. Your backend mints a short-lived token; the SDK sends it and
  refreshes it before it expires.
- Fixed `getAll()` stopping at the first 100 documents.
- Fixed documents deleted on the server reappearing while offline.
- Reads at app start now come from the server instead of a stale local copy.
- Fixed an app started offline never connecting once the network returned.
- Debug logs no longer include tokens, keys or document contents.
- Only `rv_live_` API keys are accepted.

## 0.1.0

- First release.
