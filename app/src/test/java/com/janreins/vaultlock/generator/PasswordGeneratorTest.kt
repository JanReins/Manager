package com.janreins.vaultlock.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `generate respects length bounds between 8 and 64`() {
        // Test lower bound enforcement
        val shortOptions = GeneratorOptions(length = 4)
        val shortPw = PasswordGenerator.generate(shortOptions)
        assertEquals(8, shortPw.length)

        // Test upper bound enforcement
        val longOptions = GeneratorOptions(length = 100)
        val longPw = PasswordGenerator.generate(longOptions)
        assertEquals(64, longPw.length)

        // Test exact length within bounds
        val exactOptions = GeneratorOptions(length = 20)
        val exactPw = PasswordGenerator.generate(exactOptions)
        assertEquals(20, exactPw.length)
    }

    @Test
    fun `generate includes requested charsets`() {
        val uppercaseOnly = GeneratorOptions(
            length = 16,
            includeUppercase = true,
            includeLowercase = false,
            includeNumbers = false,
            includeSymbols = false
        )
        val pwUpper = PasswordGenerator.generate(uppercaseOnly)
        assertTrue(pwUpper.all { it.isUpperCase() })

        val numbersOnly = GeneratorOptions(
            length = 12,
            includeUppercase = false,
            includeLowercase = false,
            includeNumbers = true,
            includeSymbols = false
        )
        val pwNumbers = PasswordGenerator.generate(numbersOnly)
        assertTrue(pwNumbers.all { it.isDigit() })
    }

    @Test
    fun `generate easy to read avoids ambiguous characters`() {
        val options = GeneratorOptions(
            length = 30,
            easyToRead = true
        )
        val password = PasswordGenerator.generate(options)
        val ambiguousChars = setOf('1', 'l', 'I', '0', 'O')
        assertFalse(password.any { it in ambiguousChars })
    }

    @Test
    fun `evaluateStrength returns appropriate score and label`() {
        val emptyStrength = PasswordGenerator.evaluateStrength("")
        assertEquals("Empty", emptyStrength.label)
        assertEquals(0f, emptyStrength.score)

        val strongStrength = PasswordGenerator.evaluateStrength("K9#mP!2xL$8vQ@1z")
        assertNotNull(strongStrength.label)
        assertTrue(strongStrength.score > 0.5f)
    }
}
