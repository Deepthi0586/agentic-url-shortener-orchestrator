package com.saigangili.shortener.controller;

import com.saigangili.shortener.model.UrlMapping;
import com.saigangili.shortener.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortUrlController {

    // NOTE: Authentication (API key / token) for management endpoints is assumed to be
    // enforced by a security filter/interceptor upstream (e.g. Spring Security), not
    // implemented in this controller. The redirect endpoint below remains public.
    // Analytics endpoint (GET /urls/{code}/analytics) is out of scope for this pass.

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping("/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(@RequestBody CreateUrlRequest request,
                                                              HttpServletRequest httpRequest) {
        String ownerId = resolveOwnerId(httpRequest);
        UrlMapping mapping = shortUrlService.createShortUrl(request.longUrl(), request.customAlias(), ownerId);
        CreateUrlResponse response = new CreateUrlResponse(mapping.getShortCode(), mapping.getLongUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/urls/{code}")
    public ResponseEntity<UrlMetadataResponse> getMetadata(@PathVariable("code") String code) {
        UrlMapping mapping = shortUrlService.getMetadata(code);
        UrlMetadataResponse response = new UrlMetadataResponse(
                mapping.getShortCode(),
                mapping.getLongUrl(),
                mapping.getOwnerId(),
                mapping.getCreatedAt().toString(),
                mapping.isCustomAlias(),
                mapping.getStatus().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        // NOTE: this lookup would be served from the Redis cache in a full implementation
        // to achieve sub-100ms redirect latency.
        String longUrl = shortUrlService.resolveLongUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    @DeleteMapping("/urls/{code}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable("code") String code,
                                                HttpServletRequest httpRequest) {
        String ownerId = resolveOwnerId(httpRequest);
        shortUrlService.deleteShortUrl(code, ownerId);
        return ResponseEntity.noContent().build();
    }

    private String resolveOwnerId(HttpServletRequest request) {
        // Placeholder extraction of the authenticated API consumer identity, expected
        // to be populated by an upstream auth filter (e.g. as a request attribute or header).
        String ownerId = request.getHeader("X-Api-Owner-Id");
        return ownerId != null ? ownerId : "unknown";
    }

    public record CreateUrlRequest(String longUrl, String customAlias) {
    }

    public record CreateUrlResponse(String shortCode, String longUrl) {
    }

    public record UrlMetadataResponse(String shortCode, String longUrl, String ownerId,
                                       String createdAt, boolean customAlias, String status) {
    }
}
