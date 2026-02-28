package com.thethirdlicense.services;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.thethirdlicense.Util.TokenUtil;
import com.thethirdlicense.controllers.TokenDTO;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.CoinOffer;
import com.thethirdlicense.models.ExternalClient;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.Token;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.CoinOfferRepository;
import com.thethirdlicense.repositories.ExternalClientRepository;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.repositories.TokenRepository;
import com.thethirdlicense.repositories.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class TokenService {

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ShareRepository shareRepository;
    private final ExternalClientRepository externalClientRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${currency.token.size:32}") // Configurable token size
    private int tokenSize;

    @Autowired
    public TokenService(ShareRepository shareRepository,TokenRepository tokenRepository, UserRepository userRepository, ExternalClientRepository externalClientRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
		this.shareRepository = shareRepository;
        this.externalClientRepository = externalClientRepository;
    }

@Transactional
public void transferCurrency(UUID senderId, UUID recipientId, double amount) {
    if (amount <= 0.0) throw new IllegalArgumentException("Invalid amount");

    User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

    User recipient = userRepository.findById(recipientId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

    if (sender.getId().equals(recipient.getId())) {
        throw new IllegalArgumentException("Cannot transfer to yourself");
    }

    List<Token> tokens = tokenRepository.findByOwnerAndRevokedFalseAndUsedFalse(sender);
    tokens.sort(Comparator.comparing(Token::getCreatedAt));

    double total = tokens.stream().mapToDouble(Token::getAmount).sum();
    final double EPS = 1e-9;

    if (total + EPS < amount) {
        throw new IllegalArgumentException("Insufficient funds");
    }

    double remaining = amount;

    for (Token t : tokens) {
        if (remaining <= EPS) break;

        double amt = t.getAmount();

        if (amt <= remaining + EPS) {
            remaining -= amt;
            t.setAmount(0.0);
            t.setUsed(true);
            tokenRepository.save(t);
        } else {
            t.setAmount(amt - remaining);
            remaining = 0.0;
            tokenRepository.save(t);
        }
    }

    // Credit recipient as a new token
    Token newToken = new Token();
    newToken.setTokenValue(TokenUtil.generateSecureToken(tokenSize));
    newToken.setOwner(recipient);
    newToken.setAmount(amount);
    newToken.setRevoked(false);
    newToken.setUsed(false);

    tokenRepository.save(newToken);
}
@Transactional
public void withdraw(UUID userId, double amount) {
    if (amount <= 0.0) throw new IllegalArgumentException("Invalid amount");

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    List<Token> tokens = tokenRepository.findByOwnerAndRevokedFalseAndUsedFalse(user);
    tokens.sort(Comparator.comparing(Token::getCreatedAt));

    double total = tokens.stream().mapToDouble(Token::getAmount).sum();
    final double EPS = 1e-9;

    if (total + EPS < amount) {
        throw new IllegalArgumentException("Insufficient funds");
    }

    double remaining = amount;

    for (Token t : tokens) {
        if (remaining <= EPS) break;

        double amt = t.getAmount();

        if (amt <= remaining + EPS) {
            remaining -= amt;
            t.setAmount(0.0);
            t.setUsed(true);
            tokenRepository.save(t);
        } else {
            t.setAmount(amt - remaining);
            remaining = 0.0;
            tokenRepository.save(t);
        }
    }

    // Later: create a WithdrawalRequest row + PayPal payout workflow
}

    public Token generateTokenForUser(UUID userId, double amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String tokenValue = TokenUtil.generateSecureToken(tokenSize);
        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setOwner(user);
        token.setAmount(amount);

        return tokenRepository.save(token);
    }

    public Token generateTokenForExternalClient(Long clientId, double amount) {
        ExternalClient client = externalClientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        String tokenValue = TokenUtil.generateSecureToken(tokenSize);
        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setExternalClient(client);
        token.setAmount(amount);

        return tokenRepository.save(token);
    }

    public boolean validateToken(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        return !token.isRevoked() && !token.isUsed();
    }

    public void revokeToken(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));

        token.setRevoked(true);
        tokenRepository.save(token);
    }

    public void markTokenAsUsed(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));

        token.setUsed(true);
        tokenRepository.save(token);
    }
    public List<TokenDTO> getTokensForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return tokenRepository.findByOwner(user).stream()
                .map(token -> new TokenDTO(
                        token.getId(),                 
                        token.getTokenValue(),
                        token.getAmount(),
                        token.isRevoked(),
                        token.isUsed(),
                        token.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
    @Service
    public class CoinMarketService {

        private final TokenRepository tokenRepository;
        private final CoinOfferRepository coinOfferRepository;

        @Autowired
        public CoinMarketService(TokenRepository tokenRepository, CoinOfferRepository coinOfferRepository) {
            this.tokenRepository = tokenRepository;
            this.coinOfferRepository = coinOfferRepository;
        }

        @Transactional
        public void createOfferFromToken(UUID userId, UUID tokenId, double coinAmount, double pricePerCoin) {

            Token token = tokenRepository.findById(tokenId)
                    .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

            //  ensure it belongs to the logged-in user (and is not an external client token)
            if (token.getOwner() == null || !token.getOwner().getId().equals(userId)) {
                throw new UnauthorizedException("You don't own this token");
            }

            if (token.isRevoked()) {
                throw new IllegalArgumentException("Token is revoked");
            }
            if (token.isUsed()) {
                throw new IllegalArgumentException("Token already used");
            }

            if (token.getAmount().compareTo(coinAmount) < 0) {
                throw new IllegalArgumentException("Not enough balance in token");
            }

            // Create offer
            CoinOffer offer = new CoinOffer();
            offer.setSeller(token.getOwner());
            offer.setSourceToken(token);                 // IMPORTANT: offer should reference Token
            offer.setCoinAmount(coinAmount);
            offer.setPricePerCoin(pricePerCoin);

            coinOfferRepository.save(offer);

            // Optional (recommended): reserve coins by decreasing token amount immediately
            // token.setAmount(token.getAmount().subtract(coinAmount));
            // tokenRepository.save(token);
        }
    }
    public double getUserCurrencyBalance(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return tokenRepository.findByOwnerAndRevokedFalseAndUsedFalse(user).stream()
                .mapToDouble(Token::getAmount)
                .sum();
    }


@Transactional
public void purchaseShare(UUID buyerId, UUID shareId, Double price) {
    if (price == null || price <= 0.0) {
        throw new IllegalArgumentException("Invalid price");
    }

    User buyer = userRepository.findById(buyerId)
            .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

    Share share = shareRepository.findById(shareId)
            .orElseThrow(() -> new IllegalArgumentException("Share not found"));

    User seller = share.getOwner();
    if (seller == null) {
        throw new IllegalArgumentException("Share has no owner");
    }
    if (seller.getId().equals(buyerId)) {
        throw new IllegalArgumentException("You already own this share");
    }
    if (!share.isForSale()) {
        throw new IllegalArgumentException("Share is not for sale");
    }

    // Load buyer tokens (only valid ones)
    List<Token> tokens = tokenRepository.findByOwnerAndRevokedFalseAndUsedFalse(buyer);
    if (tokens.isEmpty()) {
        throw new IllegalArgumentException("No available tokens to buy this share");
    }

    // Spend oldest tokens first
    tokens.sort(Comparator.comparing(Token::getCreatedAt));

    double total = tokens.stream()
            .mapToDouble(Token::getAmount)
            .sum();

    // Important: floating tolerance
    final double EPS = 1e-9;

    if (total + EPS < price) {
        throw new IllegalArgumentException("Insufficient funds. Need " + price + ", have " + total);
    }

    double remaining = price;

    for (Token t : tokens) {
        if (remaining <= EPS) break;

        double amt = t.getAmount();

        if (amt <= remaining + EPS) {
            // Spend entire token
            remaining -= amt;
            t.setAmount(0.0);
            t.setUsed(true);
            tokenRepository.save(t);
        } else {
            // Partially spend token
            t.setAmount(amt - remaining);
            remaining = 0.0;
            tokenRepository.save(t);
        }
    }

    // Credit seller: create a new token for seller
    Token sellerToken = new Token();
    sellerToken.setTokenValue(TokenUtil.generateSecureToken(tokenSize));
    sellerToken.setOwner(seller);
    sellerToken.setAmount(price);
    sellerToken.setRevoked(false);
    sellerToken.setUsed(false);
    // If your entity has createdAt default, you can remove this line.
    // sellerToken.setCreatedAt(LocalDateTime.now());

    tokenRepository.save(sellerToken);

    // Transfer ownership
    share.setOwner(buyer);
    share.setForSale(false);
    shareRepository.save(share);
}
}
