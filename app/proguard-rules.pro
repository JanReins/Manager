# VaultLock ProGuard & R8 Hardening Rules

# --- Room Database ---
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep class * implements androidx.sqlite.db.SupportSQLiteOpenHelper$Factory
-keepclassmembers class * {
    @androidx.room.Dao *;
}

# --- Jetpack Compose ---
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class * extends androidx.compose.ui.node.ModifierNodeElement {
    <init>(...);
}

# --- Biometric & Crypto ---
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

# Preserve Vault Entities & Security Preference Data Models
-keep class com.janreins.vaultlock.data.** { *; }
-keep class com.janreins.vaultlock.crypto.** { *; }

# Tink references these optional static-analysis annotations. They contain no
# runtime cryptography; suppress only the exact missing annotation types reported by R8.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
