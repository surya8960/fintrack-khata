# FinTrack Khata

Offline-first ledger app for Indian shopkeepers, money lenders and individuals.

| Module | What it does |
|---|---|
| **Udhar Khata** | Customer credit / payment ledger with running balances (*Udhar Diya* / *Payment Mila*) |
| **Interest Loans** | Fixed-principal simple-interest loans, date-range accrual with overlap protection, separate interest vs. principal repayments |
| **Accounts & Budgets** | Bank / cash / wallet accounts, income, expense and transfers, monthly budgets, savings goals |
| **Reports** | Unified history across all modules, CSV export |
| **Cloud sync** (optional) | Two-way sync with *your own* Supabase project, protected by Row Level Security |

* `app/` - Android app (Kotlin, Jetpack Compose, Material 3)
* `web/` - static web dashboard (browser-local data; cloud sync is Android-only for now)
* `supabase/migrations/` - database schema with owner-only RLS policies

## Interest rules

* Simple interest only, **no compounding**.
* Interest accrues on the **remaining principal** (principal repayments lower future interest).
* Every full month = `principal x rate / 100`. A trailing partial month is prorated by the real length of that month (28-31 days).
* Month boundaries are always counted from the original start date, so a loan that starts on the 31st does not drift after February.
* A new accrual may not overlap an already recorded period.
* Repayments can never exceed the outstanding interest / remaining principal.

## Architecture

```
ui/            Compose screens + KhataViewModel (state holder)
domain/        InterestCalculator (pure Kotlin, unit tested)
data/
  model/       @Serializable domain models
  repository/  KhataRepository, FinanceRepository (StateFlow = single source of truth)
  local/       JsonFileStore - atomic JSON persistence in app-private storage
  supabase/    SupabaseService - GoTrue auth + PostgREST over Ktor
  sync/        CloudSyncManager - upload, delete replay, download & merge
```

Data is persisted automatically after every change (`files/ledger/*.json`, atomic writes, corrupt files are
kept as `*.corrupt`). Auth tokens are held in memory only and are never written to disk or backups.
Demo data is seeded **only in debug builds**; release builds start empty.

## Getting started

**Requirements:** Android Studio (latest stable), JDK 17.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK  -> app/build/outputs/apk/debug
./gradlew lintDebug           # Android lint
```

Web dashboard:

```bash
cd web && npm start           # http://127.0.0.1:3000
npm test
```

### Cloud sync (optional)

1. Create a Supabase project and run `supabase/migrations/0001_initial_schema.sql` in the SQL editor.
2. In the app: *More -> Cloud Sync*, enter the project URL and **anon (public) key**, then sign in or register.
3. Tap **Sync All Data Now**.

Sync order: replay local deletions, upload every local row, download rows that only exist in the cloud.
On a conflict the local row wins. Never use the `service_role` key in the app.

### Release builds

Create `keystore.properties` in the project root (it is git-ignored) or set the matching environment variables:

```properties
storeFile=release.jks              # FINTRACK_KEYSTORE_FILE
storePassword=...                  # FINTRACK_KEYSTORE_PASSWORD
keyAlias=...                       # FINTRACK_KEY_ALIAS
keyPassword=...                    # FINTRACK_KEY_PASSWORD
```

```bash
./gradlew bundleRelease           # minified + resource-shrunk; unsigned if no keystore is configured
```

## Security notes

* Row Level Security is enabled on every table; there are no public policies. Each row is bound to `auth.uid()`.
* Cloud URLs must use HTTPS; cleartext traffic is disabled.
* CSV export neutralises spreadsheet formula injection.
* Backups include only the ledger directory.

## Known limitations / roadmap

* Money is stored as `Double` and rounded to 2 decimals (HALF_UP); migrate to `BigDecimal`/paise for very large ledgers.
* Sync is "local wins", without per-row timestamps; two devices editing the same row offline will not merge field by field.
* The web dashboard is a browser-local prototype (Udhar tab is static); web <-> Supabase sync is not implemented.
* Sign-in is not persisted across app restarts (tokens are intentionally memory-only).
* UI strings are inline (English/Hinglish mix); move to `strings.xml` for localisation.
* `applicationId` is still `com.aistudio.fintrack.khatas`; choose your final id before the first Play Store upload.
