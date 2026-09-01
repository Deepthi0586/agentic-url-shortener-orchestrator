package com.saigangili.shortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "url_mapping", uniqueConstraints = @UniqueConstraint(columnNames = "short_code"))
public class UrlMapping {

    public enum Status {
        ACTIVE, DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    protected UrlMapping() {
        // JPA
    }

    public UrlMapping(String shortCode, String longUrl, String ownerId, boolean customAlias) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.ownerId = ownerId;
        this.customAlias = customAlias;
        this.createdAt = Instant.now();
        this.status = Status.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
