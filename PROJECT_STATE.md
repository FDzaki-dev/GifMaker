# PROJECT_STATE.md — GifMaker

## Ringkasan
Android app konversi video → GIF, 100% on-device (tanpa upload/cloud), UI Jetpack Compose (Material3, dark theme, brand ungu `#7B5CFA`), bahasa UI Indonesia.

- Package: `com.gifmaker.app`
- minSdk 24 · targetSdk/compileSdk 34 · Kotlin 1.9.24 · AGP 8.5.2
- Build: Gradle Kotlin DSL, signing via `keystore.properties` (lokal) atau ENV/GitHub Secrets (CI)

## Batch Log
| Batch | Tanggal | Ringkasan |
|---|---|---|
| v1_Batch1 | 2026-08-12 | Bootstrap workflow Prompt-Driven Dev di atas source `GifMaker-main.zip` (import awal dari GitHub). Tambah Crash Logger bawaan, perbaiki CI agar publish GitHub Release, tambah `.gitattributes`, tambah file governance (PROJECT_STATE/CHANGELOG/FILE_MANIFEST/README). |

## Modul & Arsitektur
- **UI**: `MainActivity.kt` — single-activity Compose, state via `GifMakerViewModel` (MVI: `GifMakerIntent` → `state: StateFlow`).
- **Engine**: `GifEngine.kt` (orkestrasi) → `GifEncoder.kt` (penulisan file GIF89a) + `ColorQuantizer.kt` (palet warna).
- **App/DI**: `GifMakerApp.kt` — inisialisasi `CrashLogger` saat `onCreate()`.
- **Crash Logger** (`CrashLogger.kt`): uncaught-exception handler → tulis ke `Documents/GifMaker/logs/crash_<yyyyMMdd_HHmmss>_<UUID>.txt` via MediaStore (API 29+, tanpa permission legacy); fallback app-specific external storage di API <29. FIFO retention 50 file. Fail-safe (self try-catch).
- **NavGraph**: tidak ada (single-screen, tidak dibutuhkan).
- **DB/DAO**: tidak ada (tidak ada persistensi terstruktur; hanya file output GIF).
- **release.keystore**: tidak disertakan dalam repo (di-gitignore), dibuat runtime di CI dari secret `ANDROID_KEYSTORE_BASE64`.

## Protected Assets — Status Integritas
| Asset | Status |
|---|---|
| AndroidManifest.xml | ✅ utuh |
| build.gradle.kts (root & app) | ✅ utuh |
| settings.gradle.kts | ✅ utuh |
| MainActivity.kt | ✅ utuh |
| GifMakerApp.kt (Application) | ✏️ diedit parsial (tambah `CrashLogger.install()`) |
| NavGraph | N/A — tidak dipakai di proyek ini |
| DB Schema/DAO | N/A — tidak dipakai di proyek ini |
| release.keystore | N/A — sengaja tidak ada di repo (by design, lihat CI) |
| .gitignore | ✅ utuh |
| .gitattributes | ➕ ditambahkan (sebelumnya tidak ada) |
| .github/workflows/* | ✏️ diedit (tambah step publish GitHub Release) |

## Known Gaps / Next Steps (belum dikerjakan)
- Belum ada unit test.
- Belum ada app icon custom (masih default AGP adaptive icon template).
- Belum ada penanganan izin runtime untuk share/save hasil GIF ke luar (saat ini hanya simpan lokal, path ditampilkan di UI).
- `keystore.properties` & secrets GitHub belum diisi oleh user — wajib dijalankan manual sebelum CI bisa build signed APK (lihat skrip Termux di chat).

## Keputusan Teknis (log)
- Crash logger pakai MediaStore Documents (bukan Downloads) agar rapi dalam 1 folder `GifMaker/logs`.
- CI release pakai action `softprops/action-gh-release@v2` + `permissions: contents: write`, tag otomatis `build-<short_sha>`, `make_latest: true` — memastikan APK tampil di sidebar "Releases" repo, bukan cuma Actions Artifact (kadaluarsa/hilang setelah 90 hari).
