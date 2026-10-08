package com.janreins.vaultlock.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `pronounceable generation respects all charset combinations and bounds`() {
        for (mask in 0..15) {
            for (requestedLength in listOf(Int.MIN_VALUE, 8, 16, 64, Int.MAX_VALUE)) {
                val options = GeneratorOptions(
                    length = requestedLength,
                    includeUppercase = mask and 1 != 0,
                    includeLowercase = mask and 2 != 0,
                    includeNumbers = mask and 4 != 0,
                    includeSymbols = mask and 8 != 0,
                    easyToSay = true,
                    easyToRead = true
                )
                repeat(10) {
                    val password = PasswordGenerator.generate(options)
                    assertEquals(requestedLength.coerceIn(8, 64), password.length)
                    assertEquals(options.includeUppercase, password.any { it.isUpperCase() })
                    assertEquals(options.includeLowercase || mask == 0, password.any { it.isLowerCase() })
                    assertEquals(options.includeNumbers, password.any { it.isDigit() })
                    assertEquals(options.includeSymbols, password.any { !it.isLetterOrDigit() })
                    assertFalse(password.any { it in "1lI0O" })
                }
            }
        }
    }

    @Test
    fun `obvious repeated common and short passwords are weak`() {
        for (password in listOf("aaaaaaaaaaaaaaaaaaaa", "Ab1!Ab1!Ab1!Ab1!", "Password123!", "qwertyuiop", "1234567890", "Aa1!")) {
            assertEquals("Weak", PasswordGenerator.evaluateStrength(password).label)
        }
        assertEquals("Very strong", PasswordGenerator.evaluateStrength("K9#mP!2xL\$8vQ@1z").label)
    }

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
