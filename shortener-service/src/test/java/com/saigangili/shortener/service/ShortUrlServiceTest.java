package com.saigangili.shortener.service;

import com.saigangili.shortener.model.ShortUrl;
import com.saigangili.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortUrlServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    private ShortUrlService shortUrlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        shortUrlService = new ShortUrlService(shortUrlRepository);
    }

    @Test
    void createShortUrl_generatesUniqueCodeAndSaves() {
        when(shortUrlRepository.existsByCode(anyString())).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = shortUrlService.createShortUrl("https://example.com", "meta");

        assertNotNull(result);
        assertEquals("https://example.com", result.getOriginalUrl());
        assertEquals("meta", result.getMetadata());
        assertNotNull(result.getCode());
        assertEquals(7, result.getCode().length());
        assertNotNull(result.getCreatedAt());

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(shortUrlRepository, times(1)).save(captor.capture());
        assertEquals(result.getCode(), captor.getValue().getCode());
    }

    @Test
    void createShortUrl_retriesOnCollisionUntilUniqueCodeFound() {
        when(shortUrlRepository.existsByCode(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = shortUrlService.createShortUrl("https://retry.com", null);

        assertNotNull(result);
        verify(shortUrlRepository, times(3)).existsByCode(anyString());
        verify(shortUrlRepository, times(1)).save(any(ShortUrl.class));
    }

    @Test
    void getByCode_returnsShortUrlWhenFound() {
        ShortUrl existing = new ShortUrl("abc1234", "https://found.com", java.time.Instant.now(), null);
        when(shortUrlRepository.findByCode("abc1234")).thenReturn(Optional.of(existing));

        ShortUrl result = shortUrlService.getByCode("abc1234");

        assertEquals(existing, result);
    }

    @Test
    void getByCode_throwsWhenNotFound() {
        when(shortUrlRepository.findByCode("missing")).thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(NoSuchElementException.class,
                () -> shortUrlService.getByCode("missing"));
        assertEquals("Short URL not found for code: missing", ex.getMessage());
    }

    @Test
    void resolveRedirectUrl_returnsOriginalUrl() {
        ShortUrl existing = new ShortUrl("xyz9999", "https://redirect-target.com", java.time.Instant.now(), null);
        when(shortUrlRepository.findByCode("xyz9999")).thenReturn(Optional.of(existing));

        String url = shortUrlService.resolveRedirectUrl("xyz9999");

        assertEquals("https://redirect-target.com", url);
    }

    @Test
    void resolveRedirectUrl_throwsWhenNotFound() {
        when(shortUrlRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> shortUrlService.resolveRedirectUrl("nope"));
    }

    @Test
    void deleteByCode_deletesWhenExists() {
        when(shortUrlRepository.existsByCode("exists1")).thenReturn(true);

        shortUrlService.deleteByCode("exists1");

        verify(shortUrlRepository, times(1)).deleteByCode("exists1");
    }

    @Test
    void deleteByCode_throwsWhenNotFoundAndDoesNotCallDelete() {
        when(shortUrlRepository.existsByCode("missing1")).thenReturn(false);

        NoSuchElementException ex = assertThrows(NoSuchElementException.class,
                () -> shortUrlService.deleteByCode("missing1"));
        assertEquals("Short URL not found for code: missing1", ex.getMessage());
        verify(shortUrlRepository, never()).deleteByCode(anyString());
    }
}
