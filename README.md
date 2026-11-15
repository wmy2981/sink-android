# Sink for Android

![Android CI](https://github.com/wmy2981/sink-android/actions/workflows/android.yml/badge.svg)

A third-party native Android client for [Sink](https://github.com/ccbikai/Sink), the Cloudflare Workers short-link service. Built with Kotlin and Jetpack Compose, it follows Material Design 3 and connects to any self-hosted Sink instance through the Sink REST API.

**English** | [简体中文](README.zh-CN.md)

## Features

- **Connect an instance** — enter the instance URL and `NUXT_SITE_TOKEN`; the app validates them with `/api/verify` before saving.
- **Link list** — cursor pagination, pull to refresh, and infinite scrolling; search by slug, URL, or note; sort by date or slug; filter by status (active, expired, all) and tags.
- **Create, edit, delete** — target URL, custom slug, note, expiration date, and up to 10 tags.
- **Quick actions** — copy the short link, open it in a browser, share it with other apps, or show a QR code.
- **Appearance** — follow the system theme, or force light or dark; Android 12+ can use the system wallpaper palette.
- **Localized UI** — English and Simplified Chinese, following the system language.
- **Private by default** — credentials stay in the app's private DataStore and are never uploaded anywhere else.

## Tech stack

- Kotlin 2.0 + Jetpack Compose (Material 3, BOM 2024.12)
- MVVM: `ViewModel` + `StateFlow` + a small hand-written `AppContainer`
- Retrofit + OkHttp + kotlinx.serialization
- DataStore Preferences (instance URL, token, theme)
- ZXing core (QR codes)

## Build

Requirements: JDK 17, Android SDK Platform 35, Build-Tools 35.0.0.

```bash
# Optional: create a signing config (release falls back to debug signing when absent)
cat > keystore.properties <<PROPS
storeFile=keystore/sink-release.jks
storePassword=******
keyAlias=sink
keyPassword=******
PROPS

./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Debug build: `./gradlew assembleDebug`.

Unit tests: `./gradlew testDebugUnitTest` (API client, URL normalization, tags, link validation).

## Localization

The app ships with English (default) and Simplified Chinese. It follows the system language; on Android 13 and later you can also pick a language just for Sink in **Settings → Apps → Sink → App language**. Interface strings live in `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh-rCN/strings.xml`. To add a language, create `app/src/main/res/values-<qualifier>/strings.xml` and add the locale to `app/src/main/res/xml/locales_config.xml`.

## CI and APK artifacts

The GitHub Actions workflow in `.github/workflows/android.yml` runs on pushes, pull requests, tags, and manual dispatches. It:

1. runs the unit tests;
2. builds debug and release APKs;
3. uploads both APKs as the `sink-android-<run_number>` artifact, available under **Artifacts** on the run page.

Release signing is injected through repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Without those secrets (for example on pull requests from forks) the release build falls back to debug signing.

## Sink API used

| Endpoint | Purpose |
| --- | --- |
| `GET /api/verify` | Validate the token and read the current identity |
| `GET /api/link/list` | List links with pagination (sort/status/tag/cursor) |
| `GET /api/link/search` | Keyword search |
| `GET /api/link/tags` | Tags and their counts |
| `GET /api/link/query` | Read one link before editing |
| `POST /api/link/create` | Create a link |
| `PUT /api/link/edit` | Edit a link (preserving advanced fields set on the web) |
| `POST /api/link/delete` | Delete a link |

Authentication uses the `Authorization: Bearer <NUXT_SITE_TOKEN>` header.

## Known limitations

- Site-token authentication only; interactive Cloudflare Access browser login is not supported.
- Password protection, geo-routing, URL cloaking, reverse proxying, and other advanced options are not exposed in the app. Values configured on the web are preserved when editing a link.
- The device must be able to reach the instance directly. For LAN-only instances, keep the phone on the same network.

## Project structure

```
app/src/main/java/cool/sink/mobile
├── data/          # Models, Retrofit API, DataStore, repository
├── ui/            # Compose theme, components, screens, view models
├── util/          # Formatting, URL validation, QR codes
├── MainActivity.kt
└── SinkApplication.kt
```
