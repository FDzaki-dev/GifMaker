# CHANGELOG

## v1_Batch5 — 2026-08-14
### Fixed
- **Bug layout kritis**: root `Column` di layar utama pakai `Modifier.fillMaxSize().padding(...).verticalScroll(...)`. Kombinasi `fillMaxSize()` + `verticalScroll()` memaksa Column setinggi viewport walau konten lebih pendek dari layar → konten nempel di atas, sisanya jadi gap kosong raksasa (dilaporkan user via screenshot). Fix: ganti `fillMaxSize()` → `fillMaxWidth()`, tinggi Column jadi ikut konten, scroll baru aktif kalau konten benar-benar melebihi layar.

### Changed — Split ke 2 layar (Home → Editor)
Sebelumnya 1 layar dengan tab switcher; sekarang navigasi asli 2 layar terpisah (permintaan user "dibuat per layer" setelah lihat app referensi ternyata multi-screen):
- `app/build.gradle.kts` — tambah dependency `androidx.navigation:navigation-compose:2.7.7`.
- `GifMakerNavGraph.kt` **(baru)** — NavGraph dengan 2 route (`home`, `editor`); video picker dipicu sekali secara imperatif (bukan `LaunchedEffect` reactive) supaya tombol back di Editor tidak looping balik.
- `HomeScreen.kt` **(baru)** — entry point: pilih video baru, atau "Lanjut ke Editor" kalau video sudah pernah dipilih sebelumnya (resume).
- `EditorScreen.kt` **(baru)** — preview + toolbar Trim/FPS/Lebar + top bar (back arrow, check-icon generate). Berisi ulang komponen dari `MainActivity.kt` versi v1_Batch4, dengan fix bug di atas.
- `MainActivity.kt` — disederhanakan drastis: cuma `installSplashScreen()` + theme + `GifMakerNavGraph()`. Token warna brand (BackgroundDark/SurfaceDark/dst) dipindah ke top-level `val` (bukan `private`) supaya bisa dipakai lintas file/layar.

## v1_Batch4 — 2026-08-13
### Changed
- `MainActivity.kt`:
  - Tombol generate dipindah dari bottom-pinned button → ikon check (✓) di top app bar kanan atas, meniru pola save-icon app referensi (terlihat di screenshot trim & editor screen mereka).
  - Panel FPS diganti dari `Slider` kontinu → baris `FilterChip` horizontal diskrit (24/20/15/12/10/8 FPS), meniru chip-row FPS di trim screen referensi (60/40/30/25/20/15 FPS).
  - Bottom bar diganti dari 1 tombol CTA → toolbar ikon+label 3-tab (Trim/FPS/Lebar) yang men-switch panel pengaturan aktif di atasnya, meniru pola toolbar Trim/Kelola/Kecepatan/Teks di editor screen referensi (2 tab terakhir tidak diikutkan karena fitur belum ada di engine).

### Notes
- Berdasarkan 6 screenshot asli yang dikirim user: app referensi ternyata 5 layar terpisah (Home/Galeri/Trim/Editor/Export), bukan 1 layar. User memutuskan tetap pertahankan struktur 1 layar GifMaker, hanya elemen visual yang diadaptasi.
- Semua ikon tetap Material Icons bawaan (bukan hasil ekstrak dari app referensi); warna tetap brand ungu GifMaker, bukan orange/teal milik referensi.

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
