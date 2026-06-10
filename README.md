# Beacon Reporter App

**Kotlin Multiplatform + Compose Multiplatform** (Android + iOS) reporter client for
Beacon — the open-source crisis-damage crowdsourcing system. Voyager navigation + MVI +
Koin, MapLibre maps, Ktor (OkHttp/Darwin) against the live backend.

Built for the person standing in front of a damaged building:

- **Offline-first** — optimistic outbox queue with auto-flush on reconnect, offline map
  packs (MapLibre OfflineManager / MLNOfflineStorage), on-device Plus Codes (Open
  Location Code — no network, no API key).
- **Guided capture wizard** — in-app camera (CameraX / AVFoundation; **EXIF GPS/time/device
  tags stripped on capture**), 5-level EMS-98 damage grade + life-safety question,
  infrastructure type, building-footprint snap (stable building identity) with GPS and
  landmark-only fallbacks, modular secondary-impacts questions served by the backend's
  form schema, review → idempotent submit.
- **Anonymous** — no account; a random device id (`X-Device-Id`) enables "my reports",
  sync and server-derived points without name/phone/email.
- **6 UN languages + Arabic RTL**; on-device GeoJSON/CSV export via the share sheet.

## Layout

- [`/shared`](./shared/src) — common Compose UI + logic (`commonMain`), with
  `androidMain`/`iosMain` for camera/GPS/connectivity/back-handler actuals.
- [`/androidApp`](./androidApp) — Android entry point.
- [`/iosApp`](./iosApp) — iOS entry point (SwiftUI host; MapLibre via SPM).

## Build & test

The path contains a space — quote it.

```bash
./gradlew :shared:compileCommonMainKotlinMetadata    # fast shared-code compile check
./gradlew :androidApp:assembleDebug                  # Android debug APK
./gradlew :shared:compileKotlinIosSimulatorArm64     # KMP iOS compile check
open iosApp/iosApp.xcodeproj                         # full iOS app via Xcode (simulator)

./gradlew :shared:testAndroidHostTest                # Android-host tests
./gradlew :shared:iosSimulatorArm64Test              # iOS simulator tests
```

## License

Apache-2.0 — see [`LICENSE`](./LICENSE). Project docs and honest build status live in the
main Beacon repo (`docs/STATUS.md`).
