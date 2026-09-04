package com.khanabook.lite.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendErrorParserTest {

    @Test
    fun `null or blank body yields null message but preserves status code`() {
        val a = BackendErrorParser.parse(null, 500)
        assertNull(a.message)
        assertEquals(500, a.statusCode)

        val b = BackendErrorParser.parse("   ", 503)
        assertNull(b.message)
        assertEquals(503, b.statusCode)
    }

    @Test
    fun `well-formed envelope extracts error path and errorId`() {
        val body = """{"error":"Bill not found","path":"/sync/bills","errorId":"abc-123"}"""
        val parsed = BackendErrorParser.parse(body, 404)

        assertEquals("Bill not found", parsed.message)
        assertEquals("/sync/bills", parsed.path)
        assertEquals("abc-123", parsed.errorId)
        assertEquals(404, parsed.statusCode)
    }

    @Test
    fun `field errors are extracted from primitives`() {
        val body = """{"error":"Validation failed","fields":{"phone":"invalid","name":"required"}}"""
        val parsed = BackendErrorParser.parse(body, 422)

        assertEquals("invalid", parsed.fieldErrors["phone"])
        assertEquals("required", parsed.fieldErrors["name"])
    }

    @Test
    fun `array field values are joined`() {
        val body = """{"fields":{"email":["must be valid","must not be blank"]}}"""
        val parsed = BackendErrorParser.parse(body, 422)

        assertEquals("must be valid, must not be blank", parsed.fieldErrors["email"])
    }

    @Test
    fun `nested object field values are flattened`() {
        val body = """{"fields":{"address":{"city":"required","zip":"invalid"}}}"""
        val parsed = BackendErrorParser.parse(body, 422)

        // Order-independent check on the flattened content
        val flattened = parsed.fieldErrors["address"] ?: ""
        assertTrue(flattened.contains("required"))
        assertTrue(flattened.contains("invalid"))
    }

    @Test
    fun `blank field values are dropped`() {
        val body = """{"fields":{"phone":"","name":"required"}}"""
        val parsed = BackendErrorParser.parse(body, 422)

        assertNull(parsed.fieldErrors["phone"])
        assertEquals("required", parsed.fieldErrors["name"])
    }

    @Test
    fun `malformed json falls back to raw body as message`() {
        val body = "Bad Gateway"
        val parsed = BackendErrorParser.parse(body, 502)

        assertEquals("Bad Gateway", parsed.message)
        assertEquals(502, parsed.statusCode)
    }

    @Test
    fun `empty json object yields null message`() {
        val parsed = BackendErrorParser.parse("{}", 400)
        assertNull(parsed.message)
        assertEquals(400, parsed.statusCode)
    }

    @Test
    fun `sync conflict exception carries canonical message and 409`() {
        val ex = SyncConflictException(failedLocalIds = listOf(1L, 2L), syncEntityLabel = "bills")
        assertEquals(SYNC_CONFLICT_MESSAGE, ex.details.message)
        assertEquals(409, ex.details.statusCode)
        assertEquals(listOf(1L, 2L), ex.failedLocalIds)
    }
}
