package com.thethirdlicense.models;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "repository_access")
public class RepositoryAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long  id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository_ repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel accessLevel = AccessLevel.CONTRIBUTOR; // default

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    public enum AccessLevel {
        OWNER, CONTRIBUTOR, VIEWER
    }

    // === Getters and Setters ===

    public Long  getId() {
        return id;
    }

    public void setId(Long  id) {
        this.id = id;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Repository_ getRepository() {
        return this.repository;
    }

    public void setRepository(Repository_ repository) {
        this.repository = repository;
    }

    public AccessLevel getAccessLevel() {
        return this.accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

	
}
