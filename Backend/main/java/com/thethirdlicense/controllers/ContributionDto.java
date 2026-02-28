package com.thethirdlicense.controllers;

import com.thethirdlicense.models.Contribution;

import java.util.Date;
import java.util.UUID;

public class ContributionDto {
    private UUID id;
    private UUID repositoryId;
    private int codeSize;
    private boolean approved;
    private Date contributionDate;
    private String filename;
    private String branch;
    private String username;

    public ContributionDto(Contribution contribution) {
        this.id = contribution.getId();
        this.repositoryId = contribution.getRepository().getId();
        this.codeSize = contribution.getCodeSize();
        this.approved = contribution.isApproved();
        this.contributionDate = contribution.getContributionDate();
        this.filename = contribution.getFilename();         //  New
        this.branch = contribution.getBranch();             //  New
        this.username = contribution.getUser().getUsername(); //  New
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public int getCodeSize() {
        return codeSize;
    }

    public boolean isApproved() {
        return approved;
    }

    public Date getContributionDate() {
        return contributionDate;
    }

    public String getFilename() {
        return filename;
    }

    public String getBranch() {
        return branch;
    }

    public String getUsername() {
        return username;
    }
}
