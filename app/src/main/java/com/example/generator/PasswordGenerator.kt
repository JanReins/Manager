package com.example.generator

import java.security.SecureRandom
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 18,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val easyToSay: Boolean = false,
    val easyToRead: Boolean = true // Avoid ambiguous chars like 1, l, I, 0, O
)

enum class PasswordStrength(val label: String, val score: Float, val colorHex: Long) {
    VERY_WEAK("Very Weak", 0.2f, 0xFFEF4444),
    WEAK("Weak", 0.4f, 0xFFF97316),
    FAIR("Fair", 0.6f, 0xFFF59E0B),
    STRONG("Strong", 0.8f, 0xFF10B981),
    VERY_STRONG("Very Strong", 1.0f, 0xFF059669)
}

object PasswordGenerator {
    private val random = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    private const val AMBIGUOUS_CHARS = "1lI0O0o"

    fun generate(options: GeneratorOptions): String {
        if (options.easyToSay) {
            // Pronounceable password generator using alternating vowels and consonants
            return generateEasyToSay(options.length, options.includeUppercase)
        }

        var upperPool = UPPERCASE
        var lowerPool = LOWERCASE
        var numberPool = NUMBERS
        var symbolPool = SYMBOLS

        if (options.easyToRead) {
            upperPool = upperPool.filter { it !in AMBIGUOUS_CHARS }
            lowerPool = lowerPool.filter { it !in AMBIGUOUS_CHARS }
            numberPool = numberPool.filter { it !in AMBIGUOUS_CHARS }
            symbolPool = symbolPool.filter { it !in "{}[]()/\\'\"`~,;:.<>" }
        }

        val pools = mutableListOf<String>()
        val guaranteedChars = mutableListOf<Char>()

        if (options.includeUppercase && upperPool.isNotEmpty()) {
            pools.add(upperPool)
            guaranteedChars.add(upperPool[random.nextInt(upperPool.length)])
        }
        if (options.includeLowercase && lowerPool.isNotEmpty()) {
            pools.add(lowerPool)
            guaranteedChars.add(lowerPool[random.nextInt(lowerPool.length)])
        }
        if (options.includeNumbers && numberPool.isNotEmpty()) {
            pools.add(numberPool)
            guaranteedChars.add(numberPool[random.nextInt(numberPool.length)])
        }
        if (options.includeSymbols && symbolPool.isNotEmpty()) {
            pools.add(symbolPool)
            guaranteedChars.add(symbolPool[random.nextInt(symbolPool.length)])
        }

        if (pools.isEmpty()) {
            pools.add(LOWERCASE)
            guaranteedChars.add(LOWERCASE[random.nextInt(LOWERCASE.length)])
        }

        val fullPool = pools.joinToString("")
        val passwordChars = mutableListOf<Char>()
        passwordChars.addAll(guaranteedChars)

        while (passwordChars.size < options.length) {
            passwordChars.add(fullPool[random.nextInt(fullPool.length)])
        }

        // Shuffle securely
        for (i in passwordChars.indices) {
            val j = random.nextInt(passwordChars.size)
            val temp = passwordChars[i]
            passwordChars[i] = passwordChars[j]
            passwordChars[j] = temp
        }

        return passwordChars.take(options.length).joinToString("")
    }

    private fun generateEasyToSay(length: Int, uppercase: Boolean): String {
        val vowels = "aeiou"
        val consonants = "bcdfghjklmnpqrstvwxyz"
        val sb = StringBuilder()
        var useConsonant = true

        while (sb.length < length) {
            val char = if (useConsonant) {
                consonants[random.nextInt(consonants.length)]
            } else {
                vowels[random.nextInt(vowels.length)]
            }
            val formatted = if (uppercase && random.nextBoolean() && sb.isEmpty()) {
                char.uppercaseChar()
            } else {
                char
            }
            sb.append(formatted)
            useConsonant = !useConsonant
        }
        return sb.substring(0, length)
    }

    /**
     * Evaluates password strength and entropy bits.
     */
    fun evaluateStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.VERY_WEAK

        var poolSize = 0
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) return PasswordStrength.VERY_WEAK

        val entropy = password.length * log2(poolSize.toDouble())

        return when {
            entropy < 35 -> PasswordStrength.VERY_WEAK
            entropy < 50 -> PasswordStrength.WEAK
            entropy < 65 -> PasswordStrength.FAIR
            entropy < 80 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }
}
