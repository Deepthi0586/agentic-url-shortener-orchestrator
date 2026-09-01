package com.saigangili.shortener.controller;

import com.saigangili.shortener.model.ShortUrl;
import com.saigangili.shortener.service.AuthService;
import com.saigangili.shortener.service.ShortUrlService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;

// NOTE: Rate limiting and analytics event capture on these endpoints are out of
// scope for this pass and are intentionally not implemented here.
@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final AuthService authService;

    public ShortUrlController(ShortUrlService shortUrlService, AuthService authService) {
        this.shortUrlService = shortUrlService;
        this.authService = authService;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<?> issueToken(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String secret = credentials.get("password") != null ? credentials.get("password") : credentials.get("apiKey");
        try {
            String token = authService.authenticateAndGenerateToken(username, secret);
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "expiresInMs", authService.getExpirationMillis()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/urls")
    public ResponseEntity<?> createShortUrl(@RequestHeader("Authorization") String authHeader,
                                             @RequestBody Map<String, String> request) {
        ResponseEntity<?> authError = validateAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        ShortUrl created = shortUrlService.createShortUrl(request.get("originalUrl"), request.get("metadata"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/urls/{code}")
    public ResponseEntity<?> getMetadata(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable String code) {
        ResponseEntity<?> authError = validateAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        try {
            ShortUrl shortUrl = shortUrlService.getByCode(code);
            return ResponseEntity.ok(shortUrl);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/urls/{code}")
    public ResponseEntity<?> deleteShortUrl(@RequestHeader("Authorization") String authHeader,
                                             @PathVariable String code) {
        ResponseEntity<?> authError = validateAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        try {
            shortUrlService.deleteByCode(code);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> redirect(@PathVariable String code) {
        try {
            String originalUrl = shortUrlService.resolveRedirectUrl(code);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(originalUrl)).build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<?> validateAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing or invalid Authorization header"));
        }
        String token = authHeader.substring("Bearer ".length());
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(authService.getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (claims.getExpiration() != null && claims.getExpiration().before(new java.util.Date())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token expired"));
            }
            return null;
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token expired"));
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }
    }
}
