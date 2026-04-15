package com.khanabook.lite.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessageSanitizerTest {

    @Test
    fun `sanitizeBackendError preserves structured field errors`() {
        val parsed = BackendErrorParser.parse(
            """
            {
              "error": "Validation failed",
              "fields": {
                "phoneNumber": "Phone number must be 10 digits",
                "password": ["Password must contain a symbol"]
              },
              "path": "/api/v1/auth/signup",
              "errorId": "err-123"
            }
            """.trimIndent(),
            statusCode = 400
        )

        val sanitized = UserMessageSanitizer.sanitizeBackendError(parsed, "Registration failed")

        assertEquals("Validation failed", sanitized.message)
        assertEquals("Phone number must be 10 digits", sanitized.fieldErrors["phoneNumber"])
        assertEquals("Password must contain a symbol", sanitized.fieldErrors["password"])
        assertEquals("err-123", sanitized.errorId)
    }

    @Test
    fun `sanitizeBackendError hides internal messages`() {
        val parsed = ParsedBackendError(
            message = "SQLITE_CONSTRAINT at https://internal.example.com/api/users",
            fieldErrors = mapOf("phoneNumber" to "https://internal.example.com/api/users")
        )

        val sanitized = UserMessageSanitizer.sanitizeBackendError(parsed, "Unable to complete request.")

        assertEquals("Unable to complete request.", sanitized.message)
        assertTrue(sanitized.fieldErrors.isEmpty())
    }

    @Test
    fun `sanitizeBackendError maps http 409 to sync conflict message`() {
        val sanitized = UserMessageSanitizer.sanitizeBackendError(
            ParsedBackendError(message = "Optimistic locking failed", statusCode = 409),
            "Sync failed."
        )

        assertEquals(SYNC_CONFLICT_MESSAGE, sanitized.message)
    }
}
