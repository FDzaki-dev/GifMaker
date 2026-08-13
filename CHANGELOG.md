# CHANGELOG

## v1_Batch3 — 2026-08-12
### Changed
- `MainActivity.kt` — restyle penuh layar utama: `Scaffold` dengan top app bar + bottom-pinned CTA "Buat GIF", kartu preview video (thumbnail asli via `MediaMetadataRetriever`), kartu trim range slider, kartu setting FPS/lebar dengan ikon. Terinspirasi struktur umum app "GIF Maker, Video to GIF Editor" (`com.bk.videotogif`) dari deskripsi fitur publik Play Store — bukan pixel-exact clone, semua ikon/aset original.
- `GifMakerViewModel.kt` — tambah state `videoDurationMs`, `videoThumbnail`, `isLoadingVideoInfo`; `PickVideo` sekarang otomatis extract durasi+thumbnail (async, fail-safe) supaya trim slider & preview bisa jalan. Tambah `onCleared()` untuk recycle bitmap (cegah memory leak).

### Notes
- Fitur trim sebenarnya sudah ada di `GifEngine`/`GifRequest` sejak awal tapi belum pernah di-expose ke UI — batch ini baru menyambungkannya.

## v1_Batch2 — 2026-08-12
### Fixed
- `CrashLogger.kt` — `RELATIVE_DIR` diubah dari `const val` ke `val`. Root cause CI gagal (`compileReleaseKotlin FAILED`): `const val` di Kotlin wajib initializer compile-time constant murni, sedangkan `Environment.DIRECTORY_DOCUMENTS` adalah field Android SDK sehingga tidak valid dipakai dalam `const val`.

### Added
- `.github/workflows/android.yml` — step baru "Upload failure logs": saat `gradle assembleRelease` gagal, `build.log` + `app/build/reports` otomatis di-upload sebagai GitHub Actions artifact bernama `log_fail_v<version>_run<run_number>` (version diambil otomatis dari `versionName` di `app/build.gradle.kts`, run number dari `github.run_number`). Job tetap ditandai failed di akhir (`exit 1` setelah upload+cleanup) agar status CI tetap akurat.

### Cara unduh artifact log kegagalan (Termux)
```bash
RUN_ID=$(gh run list --workflow=android.yml --status failure --limit 1 --json databaseId -q '.[0].databaseId') && gh run download "$RUN_ID" --dir ~/storage/downloads/ --pattern "log_fail_v*_run*"
```

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
