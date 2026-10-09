package com.janreins.vaultlock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityPreferencesMigrationTest {
    @Test fun `failed encrypted commit retains legacy credentials and retry succeeds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = context.getSharedPreferences("vaultlock_security_prefs", Context.MODE_PRIVATE)
        val destination = context.getSharedPreferences("migration_destination", Context.MODE_PRIVATE)
        destination.edit().clear().commit()
        legacy.edit().clear().putBoolean("key_is_setup", true)
            .putString("key_master_salt", "original salt")
            .putString("key_auth_verifier", "original verifier").commit()
        val rejecting = object : SharedPreferences by destination {
            override fun edit(): SharedPreferences.Editor {
                val real = destination.edit()
                return object : SharedPreferences.Editor by real {
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                        real.putBoolean(key, value); return this
                    }
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        real.putString(key, value); return this
                    }
                    override fun commit(): Boolean = false
                }
            }
        }
        assertThrows(IllegalStateException::class.java) { SecurityPreferences(context, rejecting) }
        assertEquals("original salt", legacy.getString("key_master_salt", null))
        assertEquals("original verifier", legacy.getString("key_auth_verifier", null))
        assertFalse(destination.contains("key_is_setup"))
        val migrated = SecurityPreferences(context, destination)
        assertTrue(migrated.isMasterPasswordSet)
        assertEquals("original salt", destination.getString("key_master_salt", null))
        assertEquals("original verifier", destination.getString("key_auth_verifier", null))
        assertTrue(legacy.all.isEmpty())
    }
}
