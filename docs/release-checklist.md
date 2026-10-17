# Release Checklist

## Build and stability
- Fresh install succeeds on Android 12, 13, 14, and 15.
- API 37 emulator automation is non-gating until the AndroidX test stack supports it; use Android 12-15 devices for launch sign-off.
- Offline first run stays usable with no network dependency.
- App restart preserves local data and opens the expected surface.
- Background to foreground and device rotation do not break the active screen.
- Battery saver mode does not block core tracking flows.

## Data and export
- Existing local data survives upgrade from the current schema.
- Export creates readable markdown and PDF files.
- Open and share actions work for the latest export artifact.

## Notifications and privacy
- Notification copy matches the shipped reminder behavior.
- Privacy text matches local-first storage and reminder-preview behavior.
- Notification denial and recovery flows remain non-blocking.
- Ad privacy text matches the shipped AdMob and UMP behavior.
- The production AdMob app ID has a configured UMP privacy message.

## Store readiness
- App icon and label are final.
- No placeholder text or debug-only messaging is visible in shipping flows.
- Release builds do not emit debug-only logs.
- Play Console pre-launch report passes without blocking issues.
