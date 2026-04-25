package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.StripeSharePurchase;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.repositories.StripeSharePurchaseRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ShareStripeService {

    private final StripeService stripeService;
    private final ShareRepository shareRepository;
    private final StripeSharePurchaseRepository purchaseRepository;
    private final RepositoryAccessRepository repositoryAccessRepository;
    private final UserService userService;

    @Autowired
    public ShareStripeService(
            StripeService stripeService,
            ShareRepository shareRepository,
            StripeSharePurchaseRepository purchaseRepository,
            RepositoryAccessRepository repositoryAccessRepository,
            UserService userService) {
        this.stripeService = stripeService;
        this.shareRepository = shareRepository;
        this.purchaseRepository = purchaseRepository;
        this.repositoryAccessRepository = repositoryAccessRepository;
        this.userService = userService;
    }

    public StripeCheckoutResponse initiatePurchase(UUID buyerId, UUID shareId, String successUrl, String cancelUrl)
            throws StripeException {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        if (!share.isForSale()) {
            throw new IllegalStateException("This share is not for sale");
        }
        if (share.getOwner().getId().equals(buyerId)) {
            throw new IllegalStateException("Cannot buy your own share");
        }

        BigDecimal price = share.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Share has no valid price set");
        }

        StripeCheckoutResponse response = stripeService.createCheckoutSession(price.doubleValue(), successUrl, cancelUrl);

        StripeSharePurchase purchase = new StripeSharePurchase();
        purchase.setStripeSessionId(response.getSessionId());
        purchase.setShareId(shareId);
        purchase.setBuyerId(buyerId);
        purchase.setSellerId(share.getOwner().getId());
        purchase.setPriceUsd(price);
        purchase.setStatus(StripeSharePurchase.Status.PENDING);
        purchaseRepository.save(purchase);

        return response;
    }

    @Transactional
    public void confirmPurchase(UUID buyerId, String sessionId) throws StripeException {
        Session session = stripeService.retrieveSession(sessionId);

        if (!"complete".equalsIgnoreCase(session.getStatus()) ||
                !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe payment not completed");
        }

        StripeSharePurchase purchase = purchaseRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase record not found for session"));

        if (!purchase.getBuyerId().equals(buyerId)) {
            throw new UnauthorizedException("Not authorized to confirm this purchase");
        }
        if (purchase.getStatus() == StripeSharePurchase.Status.COMPLETED) {
            throw new IllegalStateException("Purchase already completed");
        }

        Share share = shareRepository.findById(purchase.getShareId())
                .orElseThrow(() -> new IllegalArgumentException("Share no longer exists"));

        User buyer = userService.findById(buyerId);

        share.setOwner(buyer);
        share.setForSale(false);
        shareRepository.save(share);

        for (Repository_ repo : share.getCompany().getRepositories()) {
            boolean hasAccess = repositoryAccessRepository.findByUserAndRepository(buyer, repo).isPresent();
            if (!hasAccess) {
                RepositoryAccess access = new RepositoryAccess();
                access.setUser(buyer);
                access.setRepository(repo);
                access.setAccessLevel(RepositoryAccess.AccessLevel.CONTRIBUTOR);
                access.setGrantedAt(LocalDateTime.now());
                repositoryAccessRepository.save(access);
            }
        }

        purchase.setStatus(StripeSharePurchase.Status.COMPLETED);
        purchaseRepository.save(purchase);
    }
}
