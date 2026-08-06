package com.kidskiosk.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class KioskUrlPolicyTest {

    private final KioskUrlPolicy policy = new KioskUrlPolicy("https://kiosk.example.test/tv");

    @Test
    public void allowsPathsAndQueriesOnConfiguredOrigin() {
        assertTrue(policy.isAllowed("https://kiosk.example.test/watch?id=123"));
    }

    @Test
    public void treatsDefaultPortAsSameOrigin() {
        assertTrue(policy.isAllowed("https://kiosk.example.test:443/tv"));
    }

    @Test
    public void rejectsDifferentSchemeHostAndPort() {
        assertFalse(policy.isAllowed("http://kiosk.example.test/tv"));
        assertFalse(policy.isAllowed("https://evil.example.test/tv"));
        assertFalse(policy.isAllowed("https://kiosk.example.test:444/tv"));
    }

    @Test
    public void rejectsCredentialsAndMalformedUrls() {
        assertFalse(policy.isAllowed("https://user@kiosk.example.test/tv"));
        assertFalse(policy.isAllowed("not a url"));
    }

    @Test
    public void createsExactOriginRule() {
        assertEquals("https://kiosk.example.test", policy.originRule());
        assertEquals(
                "http://192.168.1.58:8080",
                new KioskUrlPolicy("http://192.168.1.58:8080/tv").originRule());
    }

    @Test
    public void rejectsNonWebKioskOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new KioskUrlPolicy("file:///tmp/kiosk.html"));
    }
}
