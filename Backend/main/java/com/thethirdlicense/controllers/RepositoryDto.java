package com.thethirdlicense.controllers;

import java.util.List;
import java.util.UUID;

public class RepositoryDto {
    private UUID id;
    private String name;
    private String gitUrl;
    private UUID ownerId;
    private UUID companyId;
    private boolean cloned;
    private List<UUID> contributors;
    private String accessStatus; // NEW FIELD

    public RepositoryDto() {
    }

    public RepositoryDto(UUID id, String name, String gitUrl, UUID ownerId, UUID companyId) {
        this.id = id;
        this.name = name;
        this.gitUrl = gitUrl;
        this.ownerId = ownerId;
        this.companyId = companyId;
    }

    // === Getters and Setters ===
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public boolean isCloned() {
        return cloned;
    }

    public void setCloned(boolean cloned) {
        this.cloned = cloned;
    }

    public List<UUID> getContributors() {
        return contributors;
    }

    public void setContributors(List<UUID> contributors) {
        this.contributors = contributors;
    }

    public String getAccessStatus() {
        return accessStatus;
    }

    public void setAccessStatus(String accessStatus) {
        this.accessStatus = accessStatus;
    }

    public void setCompanyOwnerId(UUID id2) {
        this.ownerId = id2;
    }
}
