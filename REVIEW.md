# VaultLock review — 2026-10-08

Reviewed commit: `bdedd3b` (the repository's default branch when cloned).

**Status: improvements prepared; not ready for production credential storage.**
The release blockers below need repairs in components protected by `AGENTS.md`.
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
| Critical | Failed decryption can permanently replace credentials with blank data. `VaultEntryEntity.toDomain` catches errors and returns `[Decryption Failed]` with empty credentials. Both `reEncryptAll` and export consume those placeholder objects as valid data. | Use strict decryption for every write/export/rotation operation and abort the entire operation on any authentication failure. Give the UI an explicit unreadable-entry state that cannot be saved over accidentally. Preserve corrupt ciphertext for recovery. |
| Critical | Master-password change is not crash-safe across the database and preferences. `changeMasterPassword` writes newly encrypted rows before persisting the new salt/verifier, and ignores the Boolean returned by `commitMasterPasswordChange`. A crash or failed commit between those writes strands the vault. Existing tests simulate failure before rows change, not after. | Put vault key metadata and rotation state in the same transactional persistence boundary as entries, with a non-destructive migration from existing preferences. Introduce a serialized rotation operation and deterministic recovery for interrupted migrations; fault-test each write boundary and failed commits. Do not rely on a best-effort rollback alone. |
| High | Lock/unlock does not refresh or discard the decrypted entry flow. `getAllEntries` observes only Room emissions and samples the session key at that time. Changing `SessionManager.isUnlocked` alone does not recompute entries. The ViewModel's session observer only changes a Boolean, so unlocked credentials can remain in its lists after locking, and a new unlock can leave placeholder entries visible. | Combine session state with database emissions or switch collection on each session. Clear decrypted UI lists, search state, generated passwords, and entry selections synchronously when locking. Guard against a decryption result arriving after its session expired. Test lock/unlock without any database writes. |
| High | Password rotation can leave stale biometric credentials. If biometrics were enabled but `activity` is null, the old wrapped key survives the master-password change. Re-enrollment can also leave stale persisted tokens until its callback completes. | Atomically invalidate old biometric enrollment during rotation. Permit re-enrollment only after the new credentials are durable. Verify the unwrapped key belongs to the current vault before activating a session. Test null activity, cancellation, invalidation, and interrupted enrollment. |
| High | Several mutation coroutines have uncaught errors. `saveEntry`, `toggleFavorite`, and `deleteEntry` launch repository operations without error handling. Session expiry during editing or IO failure can terminate the app. Key derivation also runs directly on the main dispatcher. | Add guarded operations and explicit success/error results; keep failed forms open with an error. Run password derivation on a worker dispatcher and serialize authentication/rotation to prevent overlapping work. Handle coroutine cancellation separately. |
| Medium | Background clipboard clearing is not reliable. The delayed runnable attempts to read/write the clipboard after backgrounding, when Android may deny clipboard access, and lock does not attempt immediate clearing. Its closure retains copied plaintext while waiting. | Track ownership of the copied clip, attempt clearing while foregrounded at lock, cancel and release pending plaintext references, and retry ownership-aware clearing on foreground. Never erase a newer clip copied by another app. Validate on Android 26, 29, and 33+. |
| Medium | Version 1 migration uses `ALTER TABLE ... RENAME COLUMN`, unavailable on older SQLite versions shipped with supported Android 8/9 devices. | Replace the rename with a compatible create/copy/drop migration; test real legacy schemas and legacy title handling. Export Room schemas and add migration tests. |

The protected repair scope is `CryptoManager`, `SessionManager`, `SecurityPreferences`,
`VaultRepository`, `VaultDatabase` and migrations, `BiometricHelper`, plus ViewModel
authentication/session flows and their related unlock/restore UI. `TotpHelper` should also be
reviewed for locale-stable numeric formatting and malformed Base32 handling before release.
The offline policy remains mandatory: no INTERNET permission, network client, sync, or telemetry.

## Validation and limits

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
and dependencies are unchanged. Cross-store crash-safe rotation and portable backup recovery
remain open release blockers; checking the preference commit does not solve either problem.
