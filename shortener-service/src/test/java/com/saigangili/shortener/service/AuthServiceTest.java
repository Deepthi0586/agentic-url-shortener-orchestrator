package com.saigangili.shortener.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private AuthService authService;
    private static final String SECRET = "test-secret-key-that-is-long-enough-32b";
    private static final long EXPIRATION_MS = 3600000L;

    @BeforeEach
    void setUp() {
        authService = new AuthService(SECRET, EXPIRATION_MS);
    }

    @Test
    void authenticateAndGenerateToken_returnsValidTokenForCorrectCredentials() {
        String token = authService.authenticateAndGenerateToken("admin", "admin");

        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(authService.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("admin", claims.getSubject());
        assertTrue(claims.getExpiration().after(new java.util.Date()));
    }

    @Test
    void authenticateAndGenerateToken_throwsForWrongPassword() {
        assertThrows(SecurityException.class,
                () -> authService.authenticateAndGenerateToken("admin", "wrong-password"));
    }

    @Test
    void authenticateAndGenerateToken_throwsForUnknownUser() {
        assertThrows(SecurityException.class,
                () -> authService.authenticateAndGenerateToken("unknown-user", "anything"));
    }

    @Test
    void getExpirationMillis_returnsConfiguredValue() {
        assertEquals(EXPIRATION_MS, authService.getExpirationMillis());
    }
}
