package com.ichi2.testutils.common

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.jupiter.api.fail

/**
 * Tests for methods in [assertThrows]
 */
class AnkiAssertTest {

    @Test
    fun passesWithCorrectException() {
        assertThrows<IllegalArgumentException>("IllegalArgumentException is correctly detected") {
            throw IllegalArgumentException("this is expected")
        }
    }

    @Test
    fun failsWithIncorrectException() {
        try {
            assertThrows<IllegalArgumentException>("IllegalArgumentException is not found") {
                throw NullPointerException("this is unexpected")
            }
        } catch (e: AssertionError) {
            // we expect this
            assertThat(
                "message is expected",
                e.message?.contains("unexpected exception type thrown") == true
            )
            return
        }
        fail("Should have had an AssertionError")
    }

    @Test
    fun failsWithNoException() {
        try {
            assertThrows<IllegalArgumentException>("No exception is found") {
            }
        } catch (e: AssertionError) {
            // we expect this
            assertThat(
                "message is expected",
                e.message?.contains("nothing was thrown") == true
            )
            return
        }
        fail("Should have had an AssertionError")
    }
}
