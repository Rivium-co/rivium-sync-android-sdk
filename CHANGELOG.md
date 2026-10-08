# Changelog

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
