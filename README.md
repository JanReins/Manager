# VaultLock

VaultLock is a privacy-first, fully offline personal password manager for Android. Built with Modern Android Development (MAD) practices using Kotlin, Jetpack Compose, Material 3, and Room.

---

## 🔒 Security Model

VaultLock uses local encryption to protect credentials stored on-device:

- **Key Derivation (PBKDF2):** Master Key derived using `PBKDF2WithHmacSHA256` with **150,000 iterations** and a 256-bit key length.
- **AES-256-GCM Encryption:** Sensitive fields (titles, usernames, passwords, notes, TOTP secrets) are individually encrypted with `AES/GCM/NoPadding` using a cryptographically secure random 12-byte IV for every write operation.
- **Biometric Unlock:** Android `BiometricPrompt` with Hardware KeyStore wrapping to securely preserve and retrieve the master session key without compromising security. Biometric re-enrollment is required when device biometric settings change or keystore credentials are invalidated.
- **Unlock Throttling & Rate Limiting:** Consecutive failed master-password attempts trigger exponential backoff delays (1s, 2s, 4s, 8s, 16s, 32s, capped at 60s) persisted in `EncryptedSharedPreferences` to mitigate brute-force attacks.
- **Memory Hardening & Session Wiping:** Session lock clears active key references, zeroizes temporary key byte arrays, and invokes `Destroyable.destroy()` where supported. Note that due to JVM/Android garbage collection and immutable `SecretKeySpec` internal fields, full key zeroization in managed memory is best-effort and immutable copies may persist until collected by GC.
- **100% Offline Architecture:** VaultLock does not declare `android.permission.INTERNET`. Zero network dependencies, zero cloud sync, and zero external telemetry.

---

## ✨ Features (v1.1)

- **2FA Authenticator (TOTP):** Raw Base32 secrets and `otpauth://totp` URIs (RFC 6238), with SHA1/SHA256/SHA512, 6–8 digits, custom periods (15–120 seconds), and live countdowns.
- **Duplicate Password Warnings:** In-app visual warnings flagging entries reusing passwords across different services.
- **Encrypted Exports:** Save an encrypted file directly through Android's document picker. Imports are limited to 16 MiB and add entries to the current vault.
- **Biometric Authentication:** Hardware-backed fingerprint / face unlock.
- **Auto-Lock Timer:** Automatic locking on inactivity or app backgrounding.
- **Clipboard Masking:** Sensitive clip data flags (Android 13+) with clipboard clearing after 30 seconds and on vault lock, while preserving clips copied by other apps.

---

## 🛠️ Building & releases

### Prerequisites
- JDK 17 or higher
- Android SDK 36 (Build Tools 36.0.0)

### Run Unit Tests
```bash
./gradlew test
```

### Build and check a debug APK
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```
Android's default debug keystore is generated automatically; no repository keystore is needed.
The Android CI workflow runs these checks, verifies that the APK has no network permissions,
and uploads the debug APK as `app-debug-apk` plus reports. Pushing a `v*` tag creates a GitHub
Release with the debug APK. Debug APKs are for testing and use a different signing
key from a production installation; do not uninstall an existing vault to install one.

### Current recovery limitations
Existing backup files are encrypted with the installation's master key and do not include its
derivation salt. They restore only into the same vault using the original key. A fresh installation,
app reset, or master-password change cannot recover those files. Restore currently appends entries,
so importing a file repeatedly creates duplicates. Keep exports in local storage to avoid sending
them through a document provider that syncs to the cloud.

The [review and repair plan](REVIEW.md) lists unresolved security and recovery issues.
This revision is not ready to be the only store of important credentials until the release
blockers there are fixed and Android/device checks pass.

### Build Release APK
```bash
./gradlew assembleRelease
```
Release signing is optional: set `KEYSTORE_PATH` to an existing keystore file and provide non-blank
`STORE_PASSWORD` and `KEY_PASSWORD`. `KEY_ALIAS` is optional and defaults to `upload`.
Without these, the release APK is unsigned. Keep the same production signing key for updates; never commit it.

---

## 📄 License
This project is released under the MIT License.
