package com.floralens

import com.floralens.data.culture.NatureServiceEndpoints
import org.junit.Assert.*
import org.junit.Test

class NatureServiceEndpointsTest {
    @Test fun cloudbasePrefixIsRetainedForEveryOperation() {
        for (base in listOf("https://example.org/wildflower-ai", "https://example.org/wildflower-ai/", "https://example.org/wildflower-ai/v1/nature")) {
            for (operation in listOf("session", "chat", "nature"))
                assertEquals("https://example.org/wildflower-ai/v1/$operation", NatureServiceEndpoints.endpoint(base, operation))
        }
    }
    @Test fun originalWorkerAndLoopbackEndpointConfigurationRemainCompatible() {
        assertEquals("https://example.org/v1/session", NatureServiceEndpoints.endpoint("https://example.org/", "session"))
        assertEquals("http://127.0.0.1:8787/v1/chat", NatureServiceEndpoints.endpoint("http://127.0.0.1:8787/v1/nature", "chat"))
    }
    @Test fun ambiguousOrCredentialBearingBasesAreRejected() {
        listOf("https://user:password@example.org/base", "https://example.org/base?token=private", "https://example.org/base#fragment",
            "https://example.org/base/../admin", "https://example.org/base//other", "https://example.org/base%2fother", "https://example.org/%2e%2e").forEach {
            assertTrue(it, runCatching { NatureServiceEndpoints.base(it) }.isFailure)
        }
        assertTrue(runCatching { NatureServiceEndpoints.endpoint("https://example.org", "../other") }.isFailure)
    }
}
