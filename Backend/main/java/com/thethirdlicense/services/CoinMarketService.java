package com.thethirdlicense.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.CoinOffer;
import com.thethirdlicense.models.Token;
import com.thethirdlicense.repositories.CoinOfferRepository;
import com.thethirdlicense.repositories.TokenRepository;

import jakarta.transaction.Transactional;

@Service
public class CoinMarketService {

    private final TokenRepository tokenRepository;
    private final CoinOfferRepository coinOfferRepository;

    @Autowired
    public CoinMarketService(TokenRepository tokenRepository, CoinOfferRepository coinOfferRepository) {
        this.tokenRepository = tokenRepository;
        this.coinOfferRepository = coinOfferRepository;
    }

    /**
     * Creates an offer to sell coins from a source Token.
     * Assumes Token.amount is in "coins" units.
     */
    @Transactional
    public void createOfferFromToken(UUID userId, UUID tokenId, double coinAmount, double pricePerCoin) {

        if (coinAmount <= 0.0) {
            throw new IllegalArgumentException("Invalid coinAmount");
        }
        if (pricePerCoin <= 0.0) {
            throw new IllegalArgumentException("Invalid pricePerCoin");
        }

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        // Ensure this token belongs to the logged-in user
        if (token.getOwner() == null || token.getOwner().getId() == null || !token.getOwner().getId().equals(userId)) {
            throw new UnauthorizedException("You don't own this token");
        }

        if (token.isRevoked()) {
            throw new IllegalArgumentException("Token is revoked");
        }
        if (token.isUsed()) {
            throw new IllegalArgumentException("Token already used");
        }

        // Floating tolerance
        final double EPS = 1e-9;
        if (token.getAmount() + EPS < coinAmount) {
            throw new IllegalArgumentException("Not enough balance in token");
        }

        // Create offer
        CoinOffer offer = new CoinOffer();
        offer.setSeller(token.getOwner());
        offer.setSourceToken(token);          // IMPORTANT: CoinOffer must reference Token
        offer.setCoinAmount(coinAmount);
        offer.setPricePerCoin(pricePerCoin);
        offer.setActive(true);

        // If your CoinOffer has a createdAt field:
        try {
            offer.getClass().getMethod("setCreatedAt", LocalDateTime.class).invoke(offer, LocalDateTime.now());
        } catch (Exception ignored) {
            // Ignore if CoinOffer doesn't have createdAt
        }

        coinOfferRepository.save(offer);

        // OPTIONAL: Reserve coins immediately by reducing token amount (recommended to prevent double-selling)
        // token.setAmount(token.getAmount() - coinAmount);
        // tokenRepository.save(token);
    }
}
