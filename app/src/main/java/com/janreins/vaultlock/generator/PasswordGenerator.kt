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
        if (options.easyToSay) {
            return generateEasyToSay(options.length, options.includeUppercase, options.includeNumbers)
        }

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

    private fun generateEasyToSay(targetLength: Int, uppercase: Boolean, numbers: Boolean): String {
        val sb = StringBuilder()
        var isVowel = random.nextBoolean()

        while (sb.length < targetLength) {
            val nextChar = if (isVowel) {
                VOWELS[random.nextInt(VOWELS.size)]
            } else {
                CONSONANTS[random.nextInt(CONSONANTS.size)]
            }
            sb.append(nextChar)
            isVowel = !isVowel
        }

        var result = sb.toString().take(targetLength)
        if (uppercase) {
            // Capitalize random syllables
            val charArray = result.toCharArray()
            for (i in charArray.indices step 3) {
                charArray[i] = charArray[i].uppercaseChar()
            }
            result = String(charArray)
        }

        if (numbers && result.length >= 4) {
            val num = (random.nextInt(90) + 10).toString()
            result = result.dropLast(2) + num
        }

        return result
    }

    fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0f, "Empty", 0xFF64748B)
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
            else -> PasswordStrength(1.0f, "Unbreakable", 0xFF059669)
        }
    }
}
