package com.janreins.vaultlock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** In-memory editor state. Never stored in saved-instance state or persisted. */
class EntryDraft {
    var title by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var url by mutableStateOf("")
    var notes by mutableStateOf("")
    var totpSecret by mutableStateOf("")
    var category by mutableStateOf("Login")
    var isFavorite by mutableStateOf(false)
    var createdAt by mutableStateOf(System.currentTimeMillis())
    var loadedEntryId by mutableStateOf<Long?>(null)

    internal fun clear() {
        title = ""
        username = ""
        password = ""
        url = ""
        notes = ""
        totpSecret = ""
        category = "Login"
        isFavorite = false
        createdAt = 0L
        loadedEntryId = null
    }
}
