package com.janreins.vaultlock.data

import kotlinx.coroutines.sync.Mutex

/** Shared across Activity/ViewModel recreation; no database write may race a key rotation. */
internal object VaultAccess {
    val mutations = Mutex()
    val authentication = Mutex()
}
