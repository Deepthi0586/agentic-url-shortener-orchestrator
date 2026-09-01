package com.saigangili.shortener.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

// NOTE: Credential store below is a minimal in-memory/config-based implementation,
// as no existing identity store was confirmed. Refresh/revocation are out of scope.
@Service
public class AuthService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecretKey signingKey;
    private final long expirationMillis;

    // In-memory credential store: username -> Credential (hashed password + roles)
    private final Map<String, Credential> credentialStore;

    public AuthService(
            @Value("${jwt.secret:change-this-secret-change-this-secret-32b}") String jwtSecret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        this.expirationMillis = expirationMillis;
        // Default demo credential; replace with real config-based provisioning.
        this.credentialStore = Map.of(
                "admin", new Credential("admin", passwordEncoder.encode("admin"), List.of("ADMIN"))
        );
    }

    public String authenticateAndGenerateToken(String username, String rawPasswordOrApiKey) {
        Credential credential = credentialStore.get(username);
        if (credential == null || !passwordEncoder.matches(rawPasswordOrApiKey, credential.hashedSecret())) {
            throw new SecurityException("Invalid credentials");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", credential.roles())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    public Key getSigningKey() {
        return signingKey;
    }

    private record Credential(String username, String hashedSecret, List<String> roles) {
    }
}
