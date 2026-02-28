package com.thethirdlicense.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "contributions")
public class Contribution {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository_ repository;

    @ManyToOne
    @JoinColumn(name = "contributor_id")
    private User user;

    private int codeSize;
    private boolean approved;

    @Temporal(TemporalType.TIMESTAMP)
    private Date contributionDate;

    @Column(name = "filename")
    private String filename;

 
    @Column(nullable = false)
    private String branch;


    @Column(name = "message")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionStatus status; 
    @Column(name = "original_commit_hash")
    private String originalCommitHash;

    @Column(name = "new_commit_hash")
    private String newCommitHash;
    @Column(nullable = false)
    private int modifiedCodeSize;
    public String getOriginalCommitHash() {
        return originalCommitHash;
    }
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void setOriginalCommitHash(String originalCommitHash) {
        this.originalCommitHash = originalCommitHash;
    }

    public String getNewCommitHash() {
        return newCommitHash;
    }

    public void setNewCommitHash(String newCommitHash) {
        this.newCommitHash = newCommitHash;
    }

    // ==== Getters & Setters ====
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
    public ContributionStatus getStatus() {
        return status;
    }

    public void setStatus(ContributionStatus status) {
        this.status = status;
    }
    public Repository_ getRepository() {
        return repository;
    }

    public void setRepository(Repository_ repository) {
        this.repository = repository;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public int getCodeSize() {
        return codeSize;
    }

    public void setCodeSize(int codeSize) {
        this.codeSize = codeSize;
    }

    public boolean isApproved() {
        return approved;
    }
    public int getModifiedCodeSize() {
        return modifiedCodeSize;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public Date getContributionDate() {
        return contributionDate;
    }

    public void setContributionDate(Date contributionDate) {
        this.contributionDate = contributionDate;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }



    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

	public void setModifiedCodeSize(int modifiedLines) {
		this.modifiedCodeSize = modifiedLines;
		
	}
	public void setCreatedAt(Date date) {
		this.contributionDate = date;
		
	}
}
