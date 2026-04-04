package com.thethirdlicense.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

import com.thethirdlicense.controllers.AccessRequestDto;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository_ repository;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING; // Default status

    public enum Status {
        PENDING, APPROVED, DECLINED
    }

	public void setUser(User user2) {
		this.user = user2;
		
	}

	public void setRepository(Repository_ repository2) {
		this.repository = repository2;
		// TODO Auto-generated method stub
		
	}

	public void setStatus(Status pending) {
		this.status = pending;
		
	}

	public UUID getId() {
		// TODO Auto-generated method stub
		return this.id;
	}

	public Status getStatus() {
		// TODO Auto-generated method stub
		return this.status;
	}

	public Repository_ getRepository() {
		// TODO Auto-generated method stub
		return this.repository;
	}

	public User getUser() {
		// TODO Auto-generated method stub
		return this.user;
	}

	public User getRequester() {
		// TODO Auto-generated method stub
		return this.user;
	}

	public void setApproved(Status b) {
		this.status = b;
		
	}

	public Status getApproved() {
		// TODO Auto-generated method stub
		return this.status;
	}
}
