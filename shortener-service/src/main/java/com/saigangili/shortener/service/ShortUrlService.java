package com.saigangili.shortener.service;

import com.saigangili.shortener.model.UrlMapping;
import com.saigangili.shortener.repository.UrlMappingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    // NOTE: Caching (e.g. Redis read-through/write-through for short_code -> long_url),
    // click analytics event persistence, aggregation/rollup services, and rate limiting
    // are out of scope for this pass and are not implemented here.

    private static final String BASE62_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;

    private final UrlMappingRepository urlMappingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ShortUrlService(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    @Transactional
    public UrlMapping createShortUrl(String longUrl, String customAlias, String ownerId) {
        String shortCode;
        boolean isCustom = customAlias != null && !customAlias.isBlank();

        if (isCustom) {
            if (urlMappingRepository.existsByShortCode(customAlias)) {
                throw new IllegalArgumentException("Custom alias already in use: " + customAlias);
            }
            shortCode = customAlias;
        } else {
            shortCode = generateUniqueShortCode();
        }

        UrlMapping mapping = new UrlMapping(shortCode, longUrl, ownerId, isCustom);
        return urlMappingRepository.save(mapping);
        // NOTE: on successful create, a write-through cache population (short_code -> long_url)
        // would happen here in a full implementation.
    }

    public UrlMapping getMetadata(String shortCode) {
        return urlMappingRepository.findByShortCode(shortCode)
                .filter(UrlMapping::isActive)
                .orElseThrow(() -> new NoSuchElementException(shortCode));
    }

    public String resolveLongUrl(String shortCode) {
        // NOTE: in a full implementation this would first check the Redis cache
        // for sub-100ms latency, falling back to the DB on a cache miss and
        // populating the cache read-through.
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .filter(UrlMapping::isActive)
                .orElseThrow(() -> new NoSuchElementException(shortCode));
        return mapping.getLongUrl();
    }

    @Transactional
    public void deleteShortUrl(String shortCode, String ownerId) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .filter(UrlMapping::isActive)
                .orElseThrow(() -> new NoSuchElementException(shortCode));

        if (!mapping.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Not authorized to delete this short URL");
        }

        mapping.setStatus(UrlMapping.Status.DELETED);
        urlMappingRepository.save(mapping);
        // NOTE: on delete, cache invalidation for short_code -> long_url would happen here.
    }

    private String generateUniqueShortCode() {
        // Auto-generate short codes using base62 encoding of an internally generated
        // unique numeric ID. We use a DB sequence to avoid coordination overhead and
        // collision-checking bottlenecks under high write volume.
        long uniqueId = nextSequenceValue();
        return encodeBase62(uniqueId);
    }

    private long nextSequenceValue() {
        Object result = entityManager
                .createNativeQuery("SELECT nextval('url_id_seq')")
                .getSingleResult();
        return ((Number) result).longValue();
    }

    private String encodeBase62(long value) {
        if (value == 0) {
            return String.valueOf(BASE62_ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long n = value;
        while (n > 0) {
            int remainder = (int) (n % BASE);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    public static class NoSuchElementException extends RuntimeException {
        public NoSuchElementException(String shortCode) {
            super("No active short URL found for code: " + shortCode);
        }
    }
}
