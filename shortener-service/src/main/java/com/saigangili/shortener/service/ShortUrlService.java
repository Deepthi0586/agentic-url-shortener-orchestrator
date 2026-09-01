package com.saigangili.shortener.service;

import com.saigangili.shortener.model.ShortUrl;
import com.saigangili.shortener.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.NoSuchElementException;

// NOTE: Caching, analytics event storage, rate limiting and reporting/aggregation
// services are out of scope for this pass and are intentionally not implemented here.
@Service
public class ShortUrlService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortUrlRepository shortUrlRepository;

    public ShortUrlService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    public ShortUrl createShortUrl(String originalUrl, String metadata) {
        String code = generateUniqueCode();
        ShortUrl shortUrl = new ShortUrl(code, originalUrl, Instant.now(), metadata);
        return shortUrlRepository.save(shortUrl);
    }

    public ShortUrl getByCode(String code) {
        return shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Short URL not found for code: " + code));
    }

    public void deleteByCode(String code) {
        if (!shortUrlRepository.existsByCode(code)) {
            throw new NoSuchElementException("Short URL not found for code: " + code);
        }
        shortUrlRepository.deleteByCode(code);
    }

    public String resolveRedirectUrl(String code) {
        return getByCode(code).getOriginalUrl();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (shortUrlRepository.existsByCode(code));
        return code;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
