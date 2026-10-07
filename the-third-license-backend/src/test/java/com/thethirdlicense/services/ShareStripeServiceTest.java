package com.thethirdlicense.services;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareStripeServiceTest {

    @Mock private StripeService stripeService;
    @Mock private ShareRepository shareRepository;
    @Mock private StripeSharePurchaseRepository purchaseRepository;
    @Mock private RepositoryAccessRepository repositoryAccessRepository;
    @Mock private UserService userService;

    @InjectMocks
    private ShareStripeService shareStripeService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID shareId;
    private User seller;
    private User buyer;
    private Company company;
    private Share share;

    @BeforeEach
    void setUp() {
        buyerId  = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        shareId  = UUID.randomUUID();

        seller = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        seller.setId(sellerId);
        seller.setStripeAccountId("acct_seller");
        seller.setPayoutsEnabled(true);

        buyer = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        buyer.setId(buyerId);

        company = new Company("Acme", seller); // repositories initialised to new ArrayList<>()

        share = new Share();
        share.setOwner(seller);
        share.setCompany(company);
        share.setPercentage(30.0);
        share.setUnits(300_000);
        share.setForSale(true);
        share.setPrice(new BigDecimal("50.00"));
    }

    // ── initiatePurchase ──────────────────────────────────────────────────────

    @Test
    void initiatePurchase_shareNotFound_throwsIllegalArgument() {
        when(shareRepository.findById(shareId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> shareStripeService.initiatePurchase(buyerId, shareId, "http://s", "http://c"));
    }

    @Test
    void initiatePurchase_shareNotForSale_throwsIllegalState() {
        share.setForSale(false);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.initiatePurchase(buyerId, shareId, "http://s", "http://c"));
    }

    @Test
    void initiatePurchase_buyerIsOwner_throwsIllegalState() {
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.initiatePurchase(sellerId, shareId, "http://s", "http://c"));
    }

    @Test
    void initiatePurchase_nullPrice_throwsIllegalState() {
        share.setPrice(null);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.initiatePurchase(buyerId, shareId, "http://s", "http://c"));
    }

    @Test
    void initiatePurchase_zeroPrice_throwsIllegalState() {
        share.setPrice(BigDecimal.ZERO);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.initiatePurchase(buyerId, shareId, "http://s", "http://c"));
    }

    @Test
    void initiatePurchase_valid_returnsCheckoutUrlAndSavesPendingPurchase() throws StripeException {
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(stripeService.createCheckoutSession(any(BigDecimal.class), eq("acct_seller"), anyString(), anyString(), anyMap()))
                .thenReturn(new StripeCheckoutResponse("sess_abc", "https://checkout.stripe.com/sess_abc"));
        when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StripeCheckoutResponse result = shareStripeService.initiatePurchase(
                buyerId, shareId, "http://success", "http://cancel");

        assertThat(result.getSessionId()).isEqualTo("sess_abc");
        verify(purchaseRepository).save(argThat(p ->
                "sess_abc".equals(p.getStripeSessionId()) &&
                buyerId.equals(p.getBuyerId())           &&
                sellerId.equals(p.getSellerId())         &&
                "acct_seller".equals(p.getSellerAccountId()) &&
                p.getStatus() == StripeSharePurchase.Status.PENDING
        ));
    }

    // ── confirmPurchase ───────────────────────────────────────────────────────

    private StripeSharePurchase pendingPurchase(String sessionId) {
        StripeSharePurchase p = new StripeSharePurchase();
        p.setStripeSessionId(sessionId);
        p.setBuyerId(buyerId);
        p.setSellerId(sellerId);
        p.setShareId(shareId);
        p.setPriceUsd(new BigDecimal("50.00"));
        p.setStatus(StripeSharePurchase.Status.PENDING);
        return p;
    }

    private Session paidSession() throws StripeException {
        Session s = mock(Session.class);
        when(s.getStatus()).thenReturn("complete");
        when(s.getPaymentStatus()).thenReturn("paid");
        return s;
    }

    @Test
    void confirmPurchase_stripePaymentNotComplete_throwsIllegalState() throws StripeException {
        Session session = mock(Session.class);
        when(session.getStatus()).thenReturn("open");
        when(stripeService.retrieveSession("sess_abc")).thenReturn(session);

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));
    }

    @Test
    void confirmPurchase_purchaseRecordMissing_throwsIllegalArgument() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));
    }

    @Test
    void confirmPurchase_wrongBuyer_throwsUnauthorized() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);

        StripeSharePurchase purchase = pendingPurchase("sess_abc");
        purchase.setBuyerId(UUID.randomUUID()); // different user
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(purchase));

        assertThrows(UnauthorizedException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));
    }

    @Test
    void confirmPurchase_alreadyCompleted_isIdempotent() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);

        StripeSharePurchase purchase = pendingPurchase("sess_abc");
        purchase.setStatus(StripeSharePurchase.Status.COMPLETED);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(purchase));

        // The webhook may have completed it first — the buyer's confirm just succeeds
        shareStripeService.confirmPurchase(buyerId, "sess_abc");

        verify(shareRepository, never()).save(any());
        verify(stripeService, never()).refundSession(any(), anyBoolean());
    }

    @Test
    void confirmPurchase_valid_transfersOwnershipAndMarksPurchaseCompleted() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(pendingPurchase("sess_abc")));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));
        when(userService.findById(buyerId)).thenReturn(buyer);

        shareStripeService.confirmPurchase(buyerId, "sess_abc");

        assertThat(share.getOwner()).isEqualTo(buyer);
        assertThat(share.isForSale()).isFalse();
        verify(shareRepository).save(share);
        verify(purchaseRepository).save(argThat(p ->
                p.getStatus() == StripeSharePurchase.Status.COMPLETED));
    }

    @Test
    void confirmPurchase_buyerHasNoRepoAccess_grantsContributorAccess() throws StripeException {
        Repository_ repo = new Repository_();
        repo.setId(UUID.randomUUID());
        company.getRepositories().add(repo);

        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(pendingPurchase("sess_abc")));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));
        when(userService.findById(buyerId)).thenReturn(buyer);
        when(repositoryAccessRepository.findByUserAndRepository(buyer, repo)).thenReturn(Optional.empty());
        when(repositoryAccessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        shareStripeService.confirmPurchase(buyerId, "sess_abc");

        verify(repositoryAccessRepository).save(argThat(a ->
                a.getUser().equals(buyer) &&
                a.getRepository().equals(repo) &&
                a.getAccessLevel() == RepositoryAccess.AccessLevel.CONTRIBUTOR
        ));
    }

    @Test
    void confirmPurchase_buyerAlreadyHasRepoAccess_doesNotGrantDuplicate() throws StripeException {
        Repository_ repo = new Repository_();
        repo.setId(UUID.randomUUID());
        company.getRepositories().add(repo);

        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(pendingPurchase("sess_abc")));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));
        when(userService.findById(buyerId)).thenReturn(buyer);
        when(repositoryAccessRepository.findByUserAndRepository(buyer, repo))
                .thenReturn(Optional.of(new RepositoryAccess()));

        shareStripeService.confirmPurchase(buyerId, "sess_abc");

        verify(repositoryAccessRepository, never()).save(any(RepositoryAccess.class));
    }

    // ── confirmPurchase: share no longer available → refund ──────────────────

    @Test
    void confirmPurchase_shareSoldToSomeoneElse_refundsAndCancels() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        StripeSharePurchase purchase = pendingPurchase("sess_abc");
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(purchase));
        share.setForSale(false); // another buyer completed first
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));

        assertThrows(com.thethirdlicense.exceptions.PurchaseRefundedException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));

        verify(stripeService).refundSession(paid, false);
        assertThat(purchase.getStatus()).isEqualTo(StripeSharePurchase.Status.CANCELLED);
        assertThat(share.getOwner()).isSameAs(seller);
    }

    @Test
    void confirmPurchase_priceChangedAfterCheckout_refunds() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(pendingPurchase("sess_abc")));
        share.setPrice(new BigDecimal("500.00"));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));

        assertThrows(com.thethirdlicense.exceptions.PurchaseRefundedException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));

        verify(stripeService).refundSession(paid, false);
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    @Test
    void initiatePurchase_sellerWithoutPayouts_throwsIllegalState() {
        seller.setPayoutsEnabled(false);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThrows(IllegalStateException.class,
                () -> shareStripeService.initiatePurchase(buyerId, shareId, "http://s", "http://c"));
    }

    @Test
    void refund_ofDestinationCharge_reversesSellerTransfer() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        StripeSharePurchase purchase = pendingPurchase("sess_abc");
        purchase.setSellerAccountId("acct_seller");
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(purchase));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.empty()); // share gone

        assertThrows(com.thethirdlicense.exceptions.PurchaseRefundedException.class,
                () -> shareStripeService.confirmPurchase(buyerId, "sess_abc"));

        verify(stripeService).refundSession(paid, true);
    }

    @Test
    void completeFromWebhook_unknownSession_isIgnored() throws StripeException {
        when(purchaseRepository.findByStripeSessionId("cs_other")).thenReturn(Optional.empty());

        shareStripeService.completeFromWebhook("cs_other");

        verify(stripeService, never()).retrieveSession(anyString());
    }

    @Test
    void completeFromWebhook_completesWithoutBuyerSession() throws StripeException {
        Session paid = paidSession();
        when(stripeService.retrieveSession("sess_abc")).thenReturn(paid);
        when(purchaseRepository.findByStripeSessionId("sess_abc")).thenReturn(Optional.of(pendingPurchase("sess_abc")));
        StripeSharePurchase locked = pendingPurchase("sess_abc");
        when(purchaseRepository.findForUpdateByStripeSessionId("sess_abc")).thenReturn(Optional.of(locked));
        when(shareRepository.findForUpdateById(shareId)).thenReturn(Optional.of(share));
        when(userService.findById(buyerId)).thenReturn(buyer);

        shareStripeService.completeFromWebhook("sess_abc");

        assertThat(share.getOwner()).isSameAs(buyer);
        assertThat(locked.getStatus()).isEqualTo(StripeSharePurchase.Status.COMPLETED);
    }
}
