# CHANGELOG

## v1_Batch1 — 2026-08-12
### Added
- `app/src/main/java/com/gifmaker/app/CrashLogger.kt` — crash logger bawaan (MediaStore, FIFO 50, fail-safe, metadata lengkap).
- `.gitattributes` — normalisasi line-ending + penanda file binary.
- `PROJECT_STATE.md`, `CHANGELOG.md`, `FILE_MANIFEST.txt`, `README.md` — file governance proyek.

### Changed
- `GifMakerApp.kt` — panggil `CrashLogger.install(this)` di `onCreate()`.
- `.github/workflows/android.yml` — tambah step `Publish GitHub Release` (softprops/action-gh-release@v2) agar APK signed muncul di sidebar Releases repo, bukan hanya Actions Artifact. `permissions` diubah ke `contents: write`.

### Notes
- Import awal dari `GifMaker-main.zip` (raw GitHub download). Tidak ada file governance sebelumnya → dianggap Initial Setup.
- Tidak ada file yang dihapus pada batch ini.
