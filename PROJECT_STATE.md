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
| v1_Batch2 | 2026-08-12 | Fix bug CI: `CrashLogger.kt:30` `const val` invalid (referensi field Android SDK non-constant) → diturunkan jadi `val`. Tambah fitur CI: build log & report otomatis di-upload sebagai artifact `log_fail_v<version>_run<run_number>` saat build gagal, agar mudah diunduh (lihat README/CHANGELOG untuk command Termux). |
| v1_Batch3 | 2026-08-12 | Restyle layar utama (satu-satunya layar) terinspirasi struktur umum app "GIF Maker, Video to GIF Editor" (Play Store `com.bk.videotogif`): top app bar, preview thumbnail video, trim range slider (fitur trim di `GifEngine` sudah ada sejak awal tapi belum ke-expose di UI — sekarang dipakai), bottom-pinned CTA "Buat GIF". Tidak menyalin aset/icon asli app referensi — semua ikon pakai Material Icons bawaan, warna & struktur hasil adaptasi dari deskripsi fitur publik. |

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
| MainActivity.kt | ✏️ diedit parsial (v1_Batch3: restyle layar — top bar, preview thumbnail, trim slider, bottom CTA) |
| GifMakerApp.kt (Application) | ✏️ diedit parsial (tambah `CrashLogger.install()`) |
| NavGraph | N/A — tidak dipakai di proyek ini |
| DB Schema/DAO | N/A — tidak dipakai di proyek ini |
| release.keystore | N/A — sengaja tidak ada di repo (by design, lihat CI) |
| .gitignore | ✅ utuh |
| .gitattributes | ➕ ditambahkan (sebelumnya tidak ada) |
| .github/workflows/* | ✏️ diedit (v1_Batch1: publish GitHub Release · v1_Batch2: upload artifact log kegagalan `log_fail_v<version>_run<run_number>`, build job jadi 2-phase agar log tetap ke-upload sebelum job ditandai gagal) |

## Known Gaps / Next Steps (belum dikerjakan)
- Belum ada unit test.
- Belum ada app icon custom (masih default AGP adaptive icon template).
- Belum ada penanganan izin runtime untuk share/save hasil GIF ke luar (saat ini hanya simpan lokal, path ditampilkan di UI).
- `keystore.properties` & secrets GitHub sudah di-generate (lihat CHANGELOG v1_Batch2 sebelumnya / respons keystore) — pastikan sudah dijalankan di Termux.
- Layout referensi `com.bk.videotogif` (v1_Batch3) diadaptasi dari deskripsi fitur publik Play Store, BUKAN pixel-exact (tidak ada akses ke screenshot resolusi tinggi/asset asli mereka). Fitur app referensi yang belum ada di GifMaker: Photo→GIF slideshow, stiker, teks overlay, filter, model iklan/premium.

## Keputusan Teknis (log)
- Crash logger pakai MediaStore Documents (bukan Downloads) agar rapi dalam 1 folder `GifMaker/logs`.
- CI release pakai action `softprops/action-gh-release@v2` + `permissions: contents: write`, tag otomatis `build-<short_sha>`, `make_latest: true` — memastikan APK tampil di sidebar "Releases" repo, bukan cuma Actions Artifact (kadaluarsa/hilang setelah 90 hari).
