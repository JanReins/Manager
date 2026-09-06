# VaultLock

VaultLock is a privacy-first, fully offline personal password manager for Android. Built with Modern Android Development (MAD) practices using Kotlin, Jetpack Compose, Material 3, and Room.

---

## 🔒 Security Model

VaultLock enforces end-to-end local encryption to keep your credentials safe on-device:

- **Key Derivation (PBKDF2):** Master Key derived using `PBKDF2WithHmacSHA256` with **150,000 iterations** and a 256-bit key length.
- **AES-256-GCM Encryption:** Sensitive fields (titles, usernames, passwords, notes, TOTP secrets) are individually encrypted with `AES/GCM/NoPadding` using a cryptographically secure random 12-byte IV for every write operation.
- **Biometric Unlock:** Android `BiometricPrompt` with Hardware KeyStore wrapping to securely preserve and retrieve the master session key without compromising security.
- **Memory Hardening:** In-memory session key zeroization upon lock to prevent memory scraping.
- **100% Offline Architecture:** VaultLock does not declare `android.permission.INTERNET`. Zero network dependencies, zero cloud sync, and zero external telemetry.

---

## ✨ Features (v1.1)

- **2FA Authenticator (TOTP):** Pure Kotlin HMAC-SHA1 algorithm (RFC 6238) providing live 6-digit TOTP codes and countdown timers for entry items.
- **Duplicate Password Warnings:** In-app visual warnings flagging entries reusing passwords across different services.
- **Encrypted Backups:** On-demand encrypted JSON database export & restore.
- **Biometric Authentication:** Hardware-backed fingerprint / face unlock.
- **Auto-Lock Timer:** Automatic locking on inactivity or app backgrounding.
- **Clipboard Masking:** Sensitive clip data flags (Android 13+) with automatic 30-second clipboard clearing.

---

## 🛠️ Building & Testing

### Prerequisites
- JDK 17 or higher
- Android SDK 36 (Build Tools 36.0.0)

### Run Unit Tests
```bash
./gradlew test
```

### Build Release APK
```bash
./gradlew assembleRelease
```

---

## 📄 License
This project is released under the MIT License.
