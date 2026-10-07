package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.exceptions.PurchaseRefundedException;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.RepositoryAccess;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.StripeSharePurchase;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.RepositoryAccessRepository;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.repositories.StripeSharePurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
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
        if (share.getUnits() <= 0) {
            throw new IllegalStateException("This share has no ownership left to sell");
        }

        BigDecimal price = share.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Share has no valid price set");
        }

        User seller = share.getOwner();
        if (seller.getStripeAccountId() == null || !seller.isPayoutsEnabled()) {
            throw new IllegalStateException("The seller can't receive payments right now. Try again later.");
        }

        StripeCheckoutResponse response = stripeService.createCheckoutSession(
                price, seller.getStripeAccountId(), successUrl, cancelUrl,
                Map.of("share_id", shareId.toString(), "buyer_id", buyerId.toString()));

        StripeSharePurchase purchase = new StripeSharePurchase();
        purchase.setStripeSessionId(response.getSessionId());
        purchase.setShareId(shareId);
        purchase.setBuyerId(buyerId);
        purchase.setSellerId(seller.getId());
        purchase.setSellerAccountId(seller.getStripeAccountId());
        purchase.setPriceUsd(price);
        purchase.setStatus(StripeSharePurchase.Status.PENDING);
        purchaseRepository.save(purchase);

        return response;
    }

    /** Called by the buyer's browser after returning from Stripe. Idempotent for the buyer. */
    @Transactional(noRollbackFor = PurchaseRefundedException.class)
    public void confirmPurchase(UUID buyerId, String sessionId) throws StripeException {
        completePurchase(sessionId, buyerId);
    }

    /** Called by the Stripe webhook; the buyer is taken from the purchase record. */
    @Transactional(noRollbackFor = PurchaseRefundedException.class)
    public void completeFromWebhook(String sessionId) throws StripeException {
        if (purchaseRepository.findByStripeSessionId(sessionId).isEmpty()) {
            return; // a checkout that isn't a share purchase
        }
        completePurchase(sessionId, null);
    }

    /**
     * Completes a paid checkout. Locks the purchase row (so the webhook and the browser can't both
     * complete it) and the share row (so two buyers can't both take it), then re-checks the share is
     * still offered on the exact terms the buyer paid for. If not, the payment is refunded — including
     * the transfer to the seller and the platform fee — and the purchase is cancelled.
     */
    private void completePurchase(String sessionId, UUID expectedBuyerId) throws StripeException {
        Session session = stripeService.retrieveSession(sessionId);

        if (!"complete".equalsIgnoreCase(session.getStatus()) ||
                !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe payment not completed");
        }

        StripeSharePurchase purchase = purchaseRepository.findForUpdateByStripeSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase record not found for session"));

        if (expectedBuyerId != null && !purchase.getBuyerId().equals(expectedBuyerId)) {
            throw new UnauthorizedException("Not authorized to confirm this purchase");
        }
        if (purchase.getStatus() == StripeSharePurchase.Status.COMPLETED) {
            return; // already completed (e.g. by the webhook) — nothing to do
        }
        if (purchase.getStatus() == StripeSharePurchase.Status.CANCELLED) {
            throw new PurchaseRefundedException(
                    "This share was sold or changed before your payment completed. Your payment has been refunded.");
        }

        Share share = shareRepository.findForUpdateById(purchase.getShareId()).orElse(null);

        boolean stillAvailable = share != null
                && share.isForSale()
                && share.getOwner().getId().equals(purchase.getSellerId())
                && share.getPrice() != null
                && share.getPrice().compareTo(purchase.getPriceUsd()) == 0;

        if (!stillAvailable) {
            stripeService.refundSession(session, purchase.getSellerAccountId() != null);
            purchase.setStatus(StripeSharePurchase.Status.CANCELLED);
            purchaseRepository.save(purchase);
            throw new PurchaseRefundedException(
                    "This share was sold or changed before your payment completed. Your payment has been refunded.");
        }

        User buyer = userService.findById(purchase.getBuyerId());

        share.setOwner(buyer);
        share.setForSale(false);
        share.setPrice(null);
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
