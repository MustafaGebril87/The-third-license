package com.thethirdlicense.controllers;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.models.CoinOffer;
import com.thethirdlicense.models.Token;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.CoinOfferRepository;
import com.thethirdlicense.repositories.TokenRepository;
import com.thethirdlicense.repositories.UserRepository;
import com.thethirdlicense.services.StripeService;

@RestController
@RequestMapping("/api/coin-market")
public class CoinMarketplaceController {

    @Autowired
    private CoinOfferRepository offerRepo;

    @Autowired
    private TokenRepository tokenRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private StripeService stripeService;

    @PostMapping("/offer")
    public ResponseEntity<?> createOffer(@RequestParam UUID tokenId,
                                         @RequestParam int coinAmount,
                                         @RequestParam double pricePerCoin) {

        System.out.println("[CoinMarketplace] createOffer tokenId=" + tokenId +
                           ", coinAmount=" + coinAmount +
                           ", pricePerCoin=" + pricePerCoin);

        Token token = tokenRepo.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        User seller = token.getOwner();

        if (coinAmount <= 0 ||
            token.getAmount().compareTo((double) (coinAmount)) < 0) {
            return ResponseEntity.badRequest().body("Insufficient token balance");
        }

        double remaining = token.getAmount() - ((coinAmount));
        token.setAmount(remaining);
        tokenRepo.save(token);

        CoinOffer offer = new CoinOffer();
        offer.setSeller(seller);
        offer.setCoinAmount(coinAmount);
        offer.setPricePerCoin(pricePerCoin);
        offer.setSourceToken(token);
        offerRepo.save(offer);

        return ResponseEntity.ok(offer);
    }

    @PostMapping("/offer/cancel")
    public ResponseEntity<?> cancelOffer(@RequestParam UUID offerId) {
        CoinOffer offer = offerRepo.findById(offerId).orElseThrow();

        if (!offer.isActive()) {
            return ResponseEntity.badRequest().body("Offer already inactive");
        }

        Token token = offer.getSourceToken();
        token.setAmount(token.getAmount() + ((offer.getCoinAmount())));
        tokenRepo.save(token);

        offer.setActive(false);
        offerRepo.save(offer);

        return ResponseEntity.ok("Offer cancelled and coins restored");
    }

    @GetMapping("/offers")
    public ResponseEntity<?> listOffers() {
        return ResponseEntity.ok(offerRepo.findByActiveTrue());
    }

    @PostMapping("/buy")
    public ResponseEntity<?> initiatePurchase(@RequestParam UUID offerId,
                                              @RequestParam UUID buyerId) {
        CoinOffer offer = offerRepo.findById(offerId).orElseThrow();
        if (!offer.isActive()) return ResponseEntity.badRequest().body("Offer is inactive");

        double total = offer.getTotalPriceWithFee();

        try {
            StripeCheckoutResponse checkoutResponse = stripeService.createCheckoutSession(
                total,
                "http://localhost:3000/coin-market/success?offerId=" + offerId + "&buyerId=" + buyerId,
                "http://localhost:3000/coin-market/cancel"
            );

            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutResponse.getCheckoutUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Failed to create Stripe checkout session");
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPurchase(@RequestParam String sessionId,
                                             @RequestParam UUID offerId,
                                             @RequestParam UUID buyerId) {
        try {
            Session session = stripeService.retrieveSession(sessionId);

            if (!"complete".equalsIgnoreCase(session.getStatus()) ||
                    !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
                return ResponseEntity.badRequest().body("Payment not completed");
            }

            CoinOffer offer = offerRepo.findById(offerId).orElseThrow();
            User buyer = userRepo.findById(buyerId).orElseThrow();
            User seller = offer.getSeller();

            if (!offer.isActive()) {
                return ResponseEntity.badRequest().body("Offer already processed");
            }

            double coins = offer.getCoinAmount();
            double price = offer.getPricePerCoin() * coins;

            buyer.addCoins(coins);
            seller.addCoins(price * 0.99 / offer.getPricePerCoin()); // receive coins after fee
            offer.setActive(false);

            userRepo.save(buyer);
            userRepo.save(seller);
            offerRepo.save(offer);

            return ResponseEntity.ok(Map.of(
                "message", "Purchase complete",
                "coinsTransferred", coins
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Payment confirmation failed");
        }
    }
}
