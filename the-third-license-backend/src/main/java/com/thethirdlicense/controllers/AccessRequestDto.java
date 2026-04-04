package com.thethirdlicense.controllers;

import java.util.UUID;

import com.thethirdlicense.models.AccessRequest;

public class AccessRequestDto {
    private UUID id;
    private AccessRequest.Status status;
    private UUID repositoryId;
    
    // Constructors
    public AccessRequestDto() {}

    public AccessRequestDto(UUID id, AccessRequest.Status status, UUID repositoryId) {
        this.id = id;
        this.status = status;
        this.repositoryId = repositoryId;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public AccessRequest.Status getStatus() {
        return status;
    }
    public void setStatus(AccessRequest.Status status) {
        this.status = status;
    }
    public UUID getRepositoryId() {
        return repositoryId;
    }
    public void setRepositoryId(UUID repositoryId) {
        this.repositoryId = repositoryId;
    }
}
