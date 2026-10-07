package com.thethirdlicense.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
@Entity
@Table(name = "shares")
public class Share {
	@Id
	@GeneratedValue(generator = "UUID")
	@GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;


    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false) // if you want to keep DB column name
    private User user;


    /** Ownership units held. Source of truth for equity; percentage is derived from it. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long units;

    /** Cached display value: units / company.totalUnits * 100. Maintained by ShareService. */
    private double percentage;
    private boolean isForSale = false;  
    @Column(nullable = true)
    private BigDecimal price;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

	public void setUser(User user2) {
		this.user = user2;
		
	}

	public void setCompany(Company company2) {
		this.company= company2;
		
	}

	public void setPercentage(double percentage2) {
		// TODO Auto-generated method stub
		this.percentage = percentage2;
	}

	public User getOwner() {
		return this.user;
	}

	public void setForSale(boolean b) {
		this.isForSale = b;
		
	}
	public boolean isForSale() {
		return this.isForSale;
	}
    // Getters and Setters

	public void setOwner(User buyer) {
		this.user = buyer;
		
	}

	public Company getCompany() {
		// TODO Auto-generated method stub
		return this.company;
	}

	public long getUnits() {
		return units;
	}

	public void setUnits(long units) {
		this.units = units;
	}

	public double getPercentage() {
		// TODO Auto-generated method stub
		return this.percentage;
	}

	public UUID getId() {
		// TODO Auto-generated method stub
		return this.id;
	}
}

