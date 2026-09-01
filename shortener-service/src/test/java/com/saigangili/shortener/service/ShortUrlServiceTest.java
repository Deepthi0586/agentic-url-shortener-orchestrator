package com.saigangili.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saigangili.shortener.model.UrlMapping;
import com.saigangili.shortener.repository.UrlMappingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShortUrlServiceTest {

    private UrlMappingRepository repository;
    private EntityManager entityManager;
    private ShortUrlService service;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(UrlMappingRepository.class);
        entityManager = mock(EntityManager.class);
        service = new ShortUrlService(repository);

        Field emField = ShortUrlService.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(service, entityManager);
    }

    private void mockSequence(long value) {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(value);
    }

    @Test
    void createShortUrl_generatesBase62CodeWhenNoCustomAlias() {
        mockSequence(125L); // base62 encoding of 125 = "21"
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.createShortUrl("https://example.com", null, "owner1");

        assertThat(result.getShortCode()).isEqualTo("21");
        assertThat(result.isCustomAlias()).isFalse();
        assertThat(result.getLongUrl()).isEqualTo("https://example.com");
        assertThat(result.getOwnerId()).isEqualTo("owner1");
        verify(repository, never()).existsByShortCode(anyString());
        verify(repository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    void createShortUrl_encodesZeroAsFirstAlphabetChar() {
        mockSequence(0L);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.createShortUrl("https://zero.com", null, "owner1");

        assertThat(result.getShortCode()).isEqualTo("0");
    }

    @Test
    void createShortUrl_usesCustomAliasWhenAvailable() {
        when(repository.existsByShortCode("myalias")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.createShortUrl("https://example.com", "myalias", "owner1");

        assertThat(result.getShortCode()).isEqualTo("myalias");
        assertThat(result.isCustomAlias()).isTrue();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void createShortUrl_throwsWhenCustomAliasAlreadyInUse() {
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.createShortUrl("https://example.com", "taken", "owner1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taken");

        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    void resolveLongUrl_returnsLongUrlWhenActive() {
        UrlMapping mapping = new UrlMapping("abc", "https://target.com", "owner1", false);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        String longUrl = service.resolveLongUrl("abc");

        assertThat(longUrl).isEqualTo("https://target.com");
    }

    @Test
    void resolveLongUrl_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveLongUrl("missing"))
                .isInstanceOf(ShortUrlService.NoSuchElementException.class);
    }

    @Test
    void resolveLongUrl_throwsWhenDeleted() {
        UrlMapping mapping = new UrlMapping("abc", "https://target.com", "owner1", false);
        mapping.setStatus(UrlMapping.Status.DELETED);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.resolveLongUrl("abc"))
                .isInstanceOf(ShortUrlService.NoSuchElementException.class);
    }

    @Test
    void getMetadata_returnsMappingWhenActive() {
        UrlMapping mapping = new UrlMapping("abc", "https://target.com", "owner1", false);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        UrlMapping result = service.getMetadata("abc");

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void getMetadata_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMetadata("missing"))
                .isInstanceOf(ShortUrlService.NoSuchElementException.class);
    }

    @Test
    void deleteShortUrl_marksAsDeletedWhenOwnerMatches() {
        UrlMapping mapping = new UrlMapping("abc", "https://target.com", "owner1", false);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteShortUrl("abc", "owner1");

        assertThat(mapping.getStatus()).isEqualTo(UrlMapping.Status.DELETED);
        verify(repository, times(1)).save(mapping);
    }

    @Test
    void deleteShortUrl_throwsSecurityExceptionWhenOwnerMismatch() {
        UrlMapping mapping = new UrlMapping("abc", "https://target.com", "owner1", false);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.deleteShortUrl("abc", "otherOwner"))
                .isInstanceOf(SecurityException.class);

        assertThat(mapping.getStatus()).isEqualTo(UrlMapping.Status.ACTIVE);
        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    void deleteShortUrl_throwsNotFoundWhenMissing() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteShortUrl("missing", "owner1"))
                .isInstanceOf(ShortUrlService.NoSuchElementException.class);
    }
}
