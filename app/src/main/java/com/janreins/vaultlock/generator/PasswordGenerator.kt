package com.janreins.vaultlock.generator

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val easyToRead: Boolean = false, // Avoid ambiguous chars like 1, l, I, 0, O
    val easyToSay: Boolean = false   // Words / syllables style
)

data class PasswordStrength(
    val score: Float, // 0.0 to 1.0
    val label: String,
    val colorHex: Long
)

object PasswordGenerator {
    private val random = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    // Ambiguous characters removed
    private const val UPPERCASE_EASY = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWERCASE_EASY = "abcdefghijkmnopqrstuvwxyz"
    private const val NUMBERS_EASY = "23456789"

    private val VOWELS = listOf("a", "e", "i", "o", "u")
    private val CONSONANTS = listOf(
        "b", "c", "d", "f", "g", "h", "j", "k", "m",
        "n", "p", "r", "s", "t", "v", "w", "z"
    )

    fun generate(options: GeneratorOptions): String {
        val upperPool = if (options.easyToRead) UPPERCASE_EASY else UPPERCASE
        val lowerPool = if (options.easyToRead) LOWERCASE_EASY else LOWERCASE
        val numberPool = if (options.easyToRead) NUMBERS_EASY else NUMBERS
        val symbolPool = SYMBOLS

        val selectedPools = mutableListOf<String>()
        if (options.includeUppercase) selectedPools.add(upperPool)
        if (options.includeLowercase) selectedPools.add(lowerPool)
        if (options.includeNumbers) selectedPools.add(numberPool)
        if (options.includeSymbols) selectedPools.add(symbolPool)

        if (selectedPools.isEmpty()) {
            selectedPools.add(lowerPool)
        }

        val length = options.length.coerceIn(8, 64)
        if (options.easyToSay && (options.includeUppercase || options.includeLowercase)) {
            return generateEasyToSay(options, length, selectedPools)
        }
        val passwordChars = mutableListOf<Char>()

        // Ensure at least one character from each selected category
        for (pool in selectedPools) {
            passwordChars.add(pool[random.nextInt(pool.length)])
        }

        // Fill the rest randomly from combined pool
        val combinedPool = selectedPools.joinToString("")
        while (passwordChars.size < length) {
            passwordChars.add(combinedPool[random.nextInt(combinedPool.length)])
        }

        // Shuffle characters
        passwordChars.shuffle(random)
        return passwordChars.joinToString("")
    }

    private fun generateEasyToSay(options: GeneratorOptions, length: Int, pools: List<String>): String {
        val chars = CharArray(length)
        var isVowel = random.nextBoolean()
        val vowels = VOWELS.joinToString("").filter {
            !options.easyToRead || it.uppercaseChar() !in "IO"
        }
        val consonants = CONSONANTS.joinToString("")
        for (i in chars.indices) {
            val pool = if (isVowel) vowels else consonants
            val letter = pool[random.nextInt(pool.length)]
            chars[i] = if (options.includeUppercase &&
                (!options.includeLowercase || random.nextBoolean())) letter.uppercaseChar() else letter
            isVowel = !isVowel
        }
        // Reserve distinct positions so every selected class is present, even
        // with both letter cases, symbols, and readability options enabled.
        val positions = chars.indices.toMutableList().apply { shuffle(random) }
        pools.forEachIndexed { index, pool ->
            val pronounceablePool = if (pool.any { it.isLetter() }) {
                pool.filter { it.lowercaseChar() in vowels + consonants }
            } else pool
            chars[positions[index]] = pronounceablePool[random.nextInt(pronounceablePool.length)]
        }
        return String(chars)
    }

    fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0f, "Empty", 0xFF64748B)
        }

        // This meter is a heuristic, not a promise of resistance to guessing.
        val normalized = password.lowercase(java.util.Locale.ROOT)
        val commonBase = normalized.trimEnd { !it.isLetter() }
        val repeatedPattern = (1..password.length / 2).any { size ->
            password.length % size == 0 && password.chunked(size).distinct().size == 1
        }
        val sequential = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiop").any {
            normalized.length >= 4 && (it.contains(normalized) || it.reversed().contains(normalized))
        }
        if (password.length < 8 || password.toSet().size <= 2 || repeatedPattern || sequential ||
            commonBase in setOf("password", "letmein", "welcome", "admin", "qwerty", "iloveyou")) {
            return PasswordStrength(0.25f, "Weak", 0xFFEF4444)
        }

        var score = 0
        if (password.length >= 8) score += 1
        if (password.length >= 12) score += 2
        if (password.length >= 16) score += 2
        if (password.length >= 20) score += 1

        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var varietyCount = 0
        if (hasUpper) varietyCount++
        if (hasLower) varietyCount++
        if (hasDigit) varietyCount++
        if (hasSymbol) varietyCount++

        score += varietyCount * 2

        return when {
            score <= 4 -> PasswordStrength(0.25f, "Weak", 0xFFEF4444)
            score <= 8 -> PasswordStrength(0.55f, "Fair", 0xFFF59E0B)
            score <= 11 -> PasswordStrength(0.80f, "Strong", 0xFF10B981)
            else -> PasswordStrength(1.0f, "Very strong", 0xFF059669)
        }
    }
}
