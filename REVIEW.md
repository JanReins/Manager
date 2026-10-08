# VaultLock review — 2026-10-08

Reviewed commit: `bdedd3b` (the repository's default branch when cloned).

**Status: Ready for a first debug APK release; portable backup recovery remains a known limitation.**
Portable backup recovery remains open; completed repairs are marked Fixed below.
The focused data-safety follow-up below modifies protected components with explicit authorization. Proposed changes and current CI results
are available in [PR #6](https://github.com/JanReins/Manager/pull/6).

## Implemented improvements

- Password generation clamps pronounceable lengths to 8–64, respects selected character
  classes, and preserves readability constraints. Numbers-only and symbols-only modes work
  even with the pronounceable toggle selected. With no classes selected, lowercase is the
  existing fallback. The strength meter rejects some obvious repeated/common patterns and
  uses “Very strong” instead of promising “Unbreakable”; it remains a heuristic.
- Entry editing waits for the selected entry before enabling Save and preserves `createdAt`.
  Incoming database updates do not overwrite an already-loaded editing form.
- TOTP secrets are masked, use password keyboard settings, and are normalized and validated
  as Base32 before saving. The UI explicitly states the supported SHA-1/6-digit/30-second format.
- Backup files save directly through Android's document picker, replacing temporary-file
  sharing and the Base64-text fallback that the importer could not read. File IO runs off the
  UI thread, streams close reliably, read failures are visible, and imports have a 16 MiB cap.
  A busy flag prevents repeated picker launches. The UI explains the existing recovery limitation.
- Clean debug builds use the standard generated keystore. Unused cloud/network dependency
  catalog entries and Google Services/secrets plugin resolution were removed. Those catalog
  libraries were not included in the original APK; removal prevents confusion and accidental use.
- KSP was raised from 2.3.5 to 2.3.6, the minimum recommended by Android's AGP 9 migration guidance.
- CI now installs SDK 36, runs debug unit tests and lint, builds an APK, checks the packaged APK
  for network permissions, and uploads reports and the debug APK. Manual dispatch is available.
- Added generator, Base32 input, and bounded backup reader regression tests.

## Release blockers and concrete repair scope

| Priority | Finding and evidence | Required repair |
| --- | --- | --- |
| Critical | Backups cannot recover a fresh installation. `createEncryptedBackupPayload` encrypts with the session key and writes only IV+ciphertext. The original PBKDF2 salt is absent. A new installation generates another salt even with the same password; a password change discards the prior salt. | Introduce a versioned, self-contained backup envelope with salt and bounded KDF parameters. Derive a separate backup key from an explicitly entered backup password; authenticate its header. Restore must accept that password and encrypt recovered entries with the destination vault's current key. Keep legacy imports explicitly limited to the original key. |
| Fixed | Silent decryption failures previously allowed blank credentials to overwrite stored data. | Strict decryption now aborts rotation, export, and existing-entry updates on failure, preserving ciphertext (earlier PRs). |
| Fixed | Master-password rotation previously stranded rows if the process died between the Room batch and preference commit. | Pending credentials are committed before rotation; password unlock checks strict row decryption and clears or promotes the journal atomically. Commit failures retain the new session key and recovery metadata. |
| Fixed | Entry loading previously ignored lock/unlock transitions. | Session-reactive entry loading and sensitive ViewModel state clearing now refresh entries on unlock and discard them on lock (earlier PRs). |
| Fixed | Password rotation previously left stale biometric credentials. | Rotation now purges the old wrapped key before re-enrollment; null Activity leaves biometric disabled; unlock verifies the unwrapped key against the current verifier (earlier PRs). |
| Fixed | Mutation coroutines previously crashed on session expiry / IO failure; PBKDF2 ran on main. | Mutations are guarded with user-visible errors; KDF runs on a worker dispatcher (earlier PRs). |
| Fixed (PR #10) | Clipboard clearing previously retained plaintext and skipped lock. | Ownership-aware clearing without retaining plaintext; clear on lock/timeout; background keeps clip for pasting (PR #10). |
| Fixed | Version 1 migration used SQLite `RENAME COLUMN`, unsupported on Android API 26–29. | This PR rebuilds and copies the v2 table, preserving all stored fields. Added a Robolectric migration regression for APIs 26–29 without new dependencies. |

The protected repair scope is `CryptoManager`, `SessionManager`, `SecurityPreferences`,
`VaultRepository`, `VaultDatabase` and migrations, `BiometricHelper`, plus ViewModel
authentication/session flows and their related unlock/restore UI. `TotpHelper` should also be
reviewed for locale-stable numeric formatting and malformed Base32 handling before release.
The offline policy remains mandatory: no INTERNET permission, network client, sync, or telemetry.

## Validation and limits

- This migration PR: verified with `testDebugUnitTest lintDebug assembleDebug`.

- Attempted: `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon`.
- Blocked before compilation: Gradle 9.3.1 could not download from `services.gradle.org`
  (`java.net.SocketException: Network is unreachable`). No Android SDK or Kotlin compiler
  is installed in this environment. No unit-test pass or APK build is claimed.
- Static checks: git whitespace validation, XML parsing and source manifest offline policy,
  TOML/version catalog reference consistency, CI YAML parsing, and protected-file preservation.
- CI is configured but has not run for these changes. A debug APK has not been produced.
- No Android runtime, emulator, visual layout, biometric hardware, clipboard, or provider tests
  could be executed here. File-provider lifecycle/configuration changes also need device testing.
- Existing backup encryption and master-password/session behavior remain unchanged. The UI
  warning and safer file IO do not repair the recovery/security blockers above.

## Acceptance checks after protected repairs

1. Fresh-install restore using an export password, wrong password, tampered header/ciphertext,
   malformed/unsupported versions, interrupted import, and oversized input; no partial restore.
2. All fields, IDs, favorites, categories and creation dates survive a password change. Corrupted
   ciphertext aborts without rewriting any entries. Simulate failure/crash at every metadata and
   database boundary and verify recovery after restart.
3. Open an existing vault, lock it, then unlock it without database mutations; entries appear
   correctly and all decrypted ViewModel lists are empty during lock. Race lock against IO.
4. Exercise rotation with biometrics enabled, no Activity, canceled enrollment, enrolled-finger
   changes and invalidated keystore; only the current key can unlock.
5. Test Android 26/28/29/33/36, rotation, process death, background timeout, failed document
   providers, no available picker, clipboard ownership, and database versions 1/2/3 migrations.
6. Run unit tests, Android lint, instrumented migration/UI tests, and debug/release compilation;
   verify no network permission appears in either packaged manifest. Keep the existing release
   signing identity for updates and validate recovery with synthetic credentials first.

## Primary build references

- [AGP 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Android's AGP 9 migration guidance](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/SKILL.md)
- [KSP 2.3.6 release](https://github.com/google/ksp/releases/tag/2.3.6)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)

## Data-safety fixes — follow-up

Fixed session-reactive entry loading and clearing of sensitive ViewModel state on lock; strict
decryption for rotation, export, and existing-entry updates; guarded mutation/export errors (shown as a toast instead of crashing);
worker-dispatcher password derivation; setup/rotation commit-result checks; and invalidation of
stale biometric wrapping during rotation, including when no Activity is available. Rotation
prepares all rows before one Room batch insert. Added isolated Robolectric repository regressions
for session transitions, failed rotation/export/update, and successful updates.

Verified with `testDebugUnitTest lintDebug assembleDebug`. Database schema/version, encryption format, permissions,
and dependencies are unchanged. Portable backup recovery remains an open release blocker.

Crash-safe master-password rotation is fixed with a synchronously committed pending salt/verifier
journal, atomic credential promotion and journal removal, and password-unlock recovery based on
strict row decryption. Biometric unlock refuses pending rotations and rejects stale keys against
the current verifier. Added Robolectric regressions for both crash boundaries, journal cleanup,
empty-vault recovery, commit failures, biometric refusal, and key verification. Verified with
`testDebugUnitTest lintDebug assembleDebug`.
