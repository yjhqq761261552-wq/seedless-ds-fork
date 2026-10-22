package com.seedlessds.app.ra

import org.junit.Assert.*
import org.junit.Test

class RaHostOverrideTest {
    @Test fun canonicalizesLoopbackAndEndpoint() {
        for (host in listOf("127.0.0.1", "localhost", "LOCALHOST"))
            for (path in listOf("", "/", "/dorequest.php"))
                assertEquals("http://${host.lowercase()}:1234", RaHostOverride.validate(" http://$host:1234$path ").getOrThrow())
    }
    @Test fun permitsPortBounds() {
        for (port in listOf(1, 65535)) assertTrue(RaHostOverride.validate("http://localhost:$port").isSuccess)
    }
    @Test fun rejectsMissingOrInvalidPort() {
        for (target in listOf("http://localhost", "http://localhost:0", "http://localhost:65536", "http://localhost:-1", "http://localhost:port"))
            assertTrue(target, RaHostOverride.validate(target).isFailure)
    }
    @Test fun rejectsRemoteAndAmbiguousHosts() {
        for (host in listOf("example.com", "localhost.example.com", "127.0.0.2", "0.0.0.0", "[::1]", "2130706433", "127.1"))
            assertTrue(host, RaHostOverride.validate("http://$host:1234").isFailure)
    }
    @Test fun rejectsCredentialsAndExtraUrlParts() {
        for (target in listOf("http://user@localhost:1234", "http://user:pass@127.0.0.1:1234", "http://localhost:1234/?x=1", "http://localhost:1234/#x", "http://localhost:1234/other", "http://localhost:1234//dorequest.php", "http://localhost:1234/%64orequest.php"))
            assertTrue(target, RaHostOverride.validate(target).isFailure)
    }
    @Test fun rejectsNonHttpSchemesAndMalformedTargets() {
        for (target in listOf(null, "", " ", "localhost:1234", "https://localhost:1234", "file://localhost:1234", "http://local host:1234", "http://localhost:1234/\nother"))
            assertTrue(target, RaHostOverride.validate(target).isFailure)
    }
    @Test fun normalizedTargetCanBeValidatedAgain() {
        val normalized = RaHostOverride.validate("http://LOCALHOST:1234/dorequest.php").getOrThrow()
        assertEquals(normalized, RaHostOverride.validate(normalized).getOrThrow())
    }
}
