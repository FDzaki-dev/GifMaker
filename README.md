# GifMaker

Ubah video jadi GIF, 100% lokal di perangkat (tanpa upload/cloud). Android native, Jetpack Compose.

## Stack
Kotlin · Jetpack Compose (Material3) · minSdk 24 · target/compileSdk 34 · Gradle Kotlin DSL

## Build lokal
```
./gradlew assembleDebug
```

## Build release (signed)
Butuh `keystore.properties` di root (lihat `app/build.gradle.kts`) berisi:
```
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```
Atau via ENV: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## CI/CD
Push ke `main` → GitHub Actions build signed APK dan publish sebagai **GitHub Release** (tab Releases di sidebar repo), bukan hanya Actions Artifact. Secrets yang dibutuhkan: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## Log kegagalan CI
Saat `gradle assembleRelease` gagal di GitHub Actions, `build.log` + laporan build otomatis diunggah sebagai artifact bernama `log_fail_v<version>_run<run_number>`. Unduh via Termux:
```bash
RUN_ID=$(gh run list --workflow=android.yml --status failure --limit 1 --json databaseId -q '.[0].databaseId') && gh run download "$RUN_ID" --dir ~/storage/downloads/ --pattern "log_fail_v*_run*"
```

## Crash log
Exception tak tertangani otomatis disimpan ke `Documents/GifMaker/logs/` di penyimpanan device (maks 50 file, FIFO).

## Dokumen proyek
Status arsitektur, keputusan teknis, dan histori perubahan lengkap ada di `PROJECT_STATE.md` dan `CHANGELOG.md`.
