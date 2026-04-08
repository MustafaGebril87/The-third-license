package com.thethirdlicense.models;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
public class User implements UserDetails {  //  Implement UserDetails

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    private String fullName;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private int sharesOwned = 0;

    @Column(nullable = false)
    private int reputationScore = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateJoined = LocalDateTime.now();

    private LocalDateTime lastLogin;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefreshToken> refreshTokens;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("user-company")
    private List<Company> ownedCompanies;

    @ManyToMany
    @JoinTable(
            name = "user_contributions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "repository_id")
    )
    private Set<Repository_> contributions = new HashSet<>();
    // Constructors
    public User() {}

    public User(String username, String email, String password, Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.roles = roles;
        this.dateJoined = LocalDateTime.now();
        this.balance = BigDecimal.ZERO;
        this.sharesOwned = 0;
        this.reputationScore = 0;
    }
    
  
    //  Implemented UserDetails Methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // No roles/authorities for now
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

 // Allow null from DB, but normalize to 0.0 in Java logic
    @Column(name = "coin_balance")
    private Double coinBalance;   // wrapper, can be null when loaded

    public double getCoinBalance() {
        return coinBalance != null ? coinBalance : 0.0;
    }

    public void setCoinBalance(Double coinBalance) {
        this.coinBalance = coinBalance;
    }

    public void addCoins(double amount) {
        if (this.coinBalance == null) {
            this.coinBalance = 0.0;
        }
        this.coinBalance += amount;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public int getSharesOwned() { return sharesOwned; }
    public void setSharesOwned(int sharesOwned) { this.sharesOwned = sharesOwned; }
    public int getReputationScore() { return reputationScore; }
    public void setReputationScore(int reputationScore) { this.reputationScore = reputationScore; }
    public LocalDateTime getDateJoined() { return dateJoined; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public List<Company> getOwnedCompanies() { return ownedCompanies; }
    public void setOwnedCompanies(List<Company> ownedCompanies) { this.ownedCompanies = ownedCompanies; }
    public Set<Repository_> getContributions() { return contributions; }
    public void setContributions(Set<Repository_> contributions) { this.contributions = contributions; }

	public void setId(UUID id2) {
		this.id = id2;
		// TODO Auto-generated method stub
		
	}

}
