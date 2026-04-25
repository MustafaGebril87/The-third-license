package com.thethirdlicense.controllers;

import com.stripe.exception.StripeException;
import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.*;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.ShareStripeService;
import com.thethirdlicense.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock private ShareService shareService;
    @Mock private UserService userService;
    @Mock private ShareStripeService shareStripeService;

    @InjectMocks
    private ShareController controller;

    private User seller;
    private User buyer;
    private UserPrincipal sellerPrincipal;
    private UserPrincipal buyerPrincipal;
    private Company company;
    private Share share;

    @BeforeEach
    void setUp() {
        seller = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        seller.setId(UUID.randomUUID());

        buyer = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        buyer.setId(UUID.randomUUID());

        sellerPrincipal = new UserPrincipal(seller.getId(), "alice", "hashed", "alice@test.com", Collections.emptyList());
        buyerPrincipal = new UserPrincipal(buyer.getId(), "bob", "hashed", "bob@test.com", Collections.emptyList());

        company = new Company("Acme Inc", seller);

        share = new Share();
        share.setCompany(company);
        share.setOwner(seller);
        share.setPercentage(30.0);
        share.setForSale(true);
        share.setPrice(new BigDecimal("50.00"));
    }

    // ── Scenario: Authenticated user views marketplace (excludes own shares) ──

    @Test
    void getMarketplaceShares_authenticated_excludesOwnShares() {
        when(userService.findById(sellerPrincipal.getId())).thenReturn(seller);
        when(shareService.getMarketplaceShares(seller)).thenReturn(Collections.emptyList());

        ResponseEntity<List<ShareDTO>> response = controller.getMarketplaceShares(sellerPrincipal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(shareService).getMarketplaceShares(seller);
        verify(shareService, never()).getAllSharesForSale();
    }

    // ── Scenario: Unauthenticated visitor views marketplace (sees all) ────────

    @Test
    void getMarketplaceShares_anonymous_showsAllSharesForSale() {
        when(shareService.getAllSharesForSale()).thenReturn(List.of(share));

        ResponseEntity<List<ShareDTO>> response = controller.getMarketplaceShares(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(shareService).getAllSharesForSale();
        verify(shareService, never()).getMarketplaceShares(any());
    }

    // ── Scenario: User views their own shares ─────────────────────────────────

    @Test
    void getMyShares_authenticatedUser_returnsOwnShares() {
        when(userService.findById(sellerPrincipal.getId())).thenReturn(seller);
        when(shareService.getSharesForUser(seller)).thenReturn(List.of(share));

        ResponseEntity<List<ShareDTO>> response = controller.getMyShares(sellerPrincipal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getPercentage()).isEqualTo(30.0);
    }

    // ── Scenario: Unauthenticated user tries to view their shares ─────────────

    @Test
    void getMyShares_unauthenticated_throwsUnauthorizedException() {
        assertThrows(UnauthorizedException.class,
            () -> controller.getMyShares(null));
    }

    // ── Scenario: Owner marks a share for sale with a price ───────────────────

    @Test
    void markShareForSale_validShare_returns200WithUpdatedShare() {
        UUID shareId = share.getId();
        Share markedShare = new Share();
        markedShare.setPercentage(30.0);
        markedShare.setForSale(true);
        markedShare.setPrice(new BigDecimal("100.00"));
        markedShare.setOwner(seller);
        markedShare.setCompany(company);

        when(shareService.markShareForSale(shareId, new BigDecimal("100.00"))).thenReturn(markedShare);

        ResponseEntity<ShareDTO> response = controller.markShareForSale(shareId, new BigDecimal("100.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getBody().isForSale()).isTrue();
    }

    // ── Scenario: Owner removes a share from sale ─────────────────────────────

    @Test
    void unmarkShareForSale_validShare_returns200() {
        UUID shareId = share.getId();
        Share unmarked = new Share();
        unmarked.setForSale(false);
        unmarked.setOwner(seller);
        unmarked.setCompany(company);

        when(shareService.unmarkShareForSale(shareId)).thenReturn(unmarked);

        ResponseEntity<Share> response = controller.unmarkShareForSale(shareId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isForSale()).isFalse();
    }

    // ── Scenario: User splits a share ─────────────────────────────────────────

    @Test
    void splitShare_validPercentage_returnsSplitShare() {
        UUID shareId = share.getId();
        Share splitOff = new Share();
        splitOff.setPercentage(10.0);
        splitOff.setOwner(seller);
        splitOff.setCompany(company);

        when(shareService.splitShare(shareId, 10.0)).thenReturn(splitOff);

        ResponseEntity<ShareDTO> response = controller.splitShare(shareId, 10.0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPercentage()).isEqualTo(10.0);
    }

    // ── Scenario: Buyer initiates Stripe checkout to buy a share ─────────────

    @Test
    void initiateSharePurchase_validBuyer_returnsCheckoutUrl() throws StripeException {
        UUID shareId = share.getId();
        StripeCheckoutResponse fakeCheckout = new StripeCheckoutResponse("sess_123", "https://checkout.stripe.com/sess_123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(buyerPrincipal);
        when(shareStripeService.initiatePurchase(
                buyer.getId(), shareId,
                "http://localhost:5173/stripe/success",
                "http://localhost:5173/stripe/cancel"))
            .thenReturn(fakeCheckout);

        ResponseEntity<StripeCheckoutResponse> response = controller.initiateSharePurchase(
                shareId,
                "http://localhost:5173/stripe/success",
                "http://localhost:5173/stripe/cancel",
                auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSessionId()).isEqualTo("sess_123");
        assertThat(response.getBody().getCheckoutUrl()).contains("stripe.com");
        verify(shareStripeService).initiatePurchase(buyer.getId(), shareId,
                "http://localhost:5173/stripe/success", "http://localhost:5173/stripe/cancel");
    }

    // ── Scenario: Buyer confirms Stripe payment → share ownership transferred ──

    @Test
    void confirmSharePurchase_validSession_returns200() throws StripeException {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(buyerPrincipal);
        doNothing().when(shareStripeService).confirmPurchase(buyer.getId(), "sess_123");

        ResponseEntity<String> response = controller.confirmSharePurchase("sess_123", auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("purchased");
        verify(shareStripeService).confirmPurchase(buyer.getId(), "sess_123");
    }

    // ── Scenario: View shares by company ─────────────────────────────────────

    @Test
    void getSharesByCompany_returnsSharesForThatCompany() {
        UUID companyId = company.getId();
        when(shareService.getSharesByCompany(companyId)).thenReturn(List.of(share));

        ResponseEntity<List<Share>> response = controller.getSharesByCompany(companyId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}
