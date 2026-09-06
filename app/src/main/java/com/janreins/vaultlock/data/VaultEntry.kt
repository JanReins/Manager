package com.janreins.vaultlock.data

/**
 * Decrypted domain representation of a Vault item for the UI layer.
 */
data class VaultEntry(
    val id: Long = 0,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totpSecret: String = "",
    val category: String = "Login",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Extracts a clean domain or initials for badge display.
     */
    val displayBadge: String
        get() {
            if (title.isNotBlank()) {
                val words = title.trim().split("\\s+".toRegex())
                return if (words.size >= 2) {
                    "${words[0].firstOrNull() ?: 'V'}${words[1].firstOrNull() ?: 'L'}".uppercase()
                } else {
                    title.take(2).uppercase()
                }
            }
            return "VL"
        }

    /**
     * Clean readable web address for display.
     */
    val displayHost: String
        get() {
            if (url.isBlank()) return ""
            return url.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore("/")
        }
}
