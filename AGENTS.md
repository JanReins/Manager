# AGENTS.md — VaultLock Developer & Agent Guidelines

## Overview
VaultLock (`com.janreins.vaultlock`) is a privacy-first, 100% offline Android password manager and TOTP authenticator built with Kotlin, Jetpack Compose, Material 3, and Room.

## Strict Offline Integrity Policy
- VaultLock is designed as a zero-trust, 100% offline application.
- **NEVER** add `android.permission.INTERNET` or any network dependencies/libraries.
- Zero network communication, cloud sync, or external telemetry is permitted.

## Common Build & Test Commands
- **Run Unit Tests:**
  ```bash
  ./gradlew test
  ```
- **Build Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Build Release APK:**
  ```bash
  ./gradlew assembleRelease
  ```

## Protected Components (Do Not Modify unless explicitly requested)
Unless explicitly tasked for a specific session, do **NOT** modify or alter:
- **Cryptography & Security:** `CryptoManager`, PBKDF2/AES-GCM encryption routines, `TotpHelper`, `BiometricHelper`, `SessionManager`, `SecurityPreferences`.
- **Database & Persistence:** `VaultRepository`, `VaultDatabase` or Room schema migrations.
- **Authentication & Unlock UI:** Master password unlock screens, biometric prompts, or ViewModel unlock flows.
- **Permissions:** No network or unnecessary system permissions.
