package com.thethirdlicense.models;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

// New entity
@Entity
public class MergeRequest {
	 @Id
	    @GeneratedValue
	    private UUID id;

	    @ManyToOne
	    private Repository_ repository;

	    private String branch;
	    private String filePath;

	    @ManyToOne
	    private User initiator;

	    @Enumerated(EnumType.STRING)
	    private MergeRequestStatus status = MergeRequestStatus.PENDING;

	    private LocalDateTime createdAt = LocalDateTime.now();

	public void setRepository(Repository_ repo) {
		this.repository =repo;
		
	}




	public void setCreatedAt(LocalDateTime now) {
		this.createdAt = now;
		
	}




	public void setBranch(String branch2) {
		this.branch = branch;
		
	}




	public void setFilePath(String path) {
		this.filePath =path;
		
	}




	public void setInitiator(User user) {
		this.initiator = user;
		
	}




	public void setStatus(MergeRequestStatus pending) {
		this.status = pending;
		
	}

    // getters/setters...
}
