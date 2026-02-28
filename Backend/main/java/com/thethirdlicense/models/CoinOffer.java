
package com.thethirdlicense.models;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class CoinOffer {

    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "token_id")
    private Token token;
    @ManyToOne
    private User seller;

    private double coinAmount;

    private double pricePerCoin; // seller-defined price

    private boolean active = true;

    
    

    
    public double getTotalPriceWithFee() {
        return coinAmount * pricePerCoin * 1.01;
    }

	public void setSeller(User seller2) {
		this.seller = seller2;
		
	}

	public void setCoinAmount(double coinAmount2) {
		this.coinAmount = coinAmount2;
		
	}

	public void setPricePerCoin(double pricePerCoin2) {
		this.pricePerCoin = pricePerCoin2;
		
	}

	public User getSeller() {
		return this.seller;

	}

	public boolean isActive() {
		
		return this.active;
	}

	public double getCoinAmount() {
		return this.coinAmount;
	}

	public double getPricePerCoin() {
		return this.pricePerCoin;
	}

	public void setActive(boolean b) {
		this.active = b;
		
	}

	public void setSourceToken(Token token) {
		this.token = token;
	}

	public Token getSourceToken() {
		// TODO Auto-generated method stub
		return this.token;
	}

}
