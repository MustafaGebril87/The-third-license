package com.thethirdlicense.models;

import jakarta.persistence.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "repositories")
public class Repository_ {
	@Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
	@Column(name = "id", columnDefinition = "uuid")
	private UUID id;

	private String Statue;;
    private String name;
    private String gitUrl;

    @ManyToOne
    @JoinColumn(name = "company_id")
    @JsonBackReference("company-repo")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contribution> contributions = new ArrayList<>();
    
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL)
    private List<RepositoryAccess> accesses;
    @Transient
    private boolean cloned;

    public boolean isCloned() {
        return cloned;
    }

    public void setCloned(boolean cloned) {
        this.cloned = cloned;
    }
	public void setCompany(Company company2) {
		this.company = company2;
		
	}
	public List<Contribution> getContributions() {
        return contributions;
    }

    public void setContributions(List<Contribution> contributions) {
        this.contributions = contributions;
    }

    public void addContribution(Contribution contribution) {
        contributions.add(contribution);
        contribution.setRepository(this);
    }

    public void removeContribution(Contribution contribution) {
        contributions.remove(contribution);
        contribution.setRepository(null);
    }

	public Company getCompany() {
		return this.company;
		
	}
	public String getGitUrl() {
		
		return this.gitUrl;
	}

	public String getName() {
		return this.name;
	}
	public User getOwner() {
		return this.owner;
	}

	public List<RepositoryAccess> getAccesses() {
		return accesses;
	}
	public void setName(String string) {
		this.name = string;
		
	}
	public void setGitUrl(String string) {
		this.gitUrl = string;
		
	}
	public void setOwner(User owner2) {
		this.owner = owner2;
		
	}
	public void setId(UUID randomUUID) {
		this.id = 		randomUUID;
	}
	public UUID getId() {
		return this.id;
	}
	public String getLocalPath() {
		// TODO Auto-generated method stub
		return this.gitUrl;
	}

	public void setStatus(String string) {
		this.Statue = string;
		
	}

    
    // Getters and Setters
}
