package com.thethirdlicense.scenarios;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.models.StripeSharePurchase;
import com.thethirdlicense.services.StripeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scenario: Share marketplace — full lifecycle with Stripe payment
 *
 * Cast of characters:
 *   - Alice (seller): owns a 30% share in Acme Corp
 *   - Bob (buyer): wants to buy a share and gain access to Acme's repository
 *
 * Steps:
 *   1. Seed Alice + Bob + Acme company + repository + share
 *   2. Alice marks the share for sale at $50
 *   3. Bob browses the marketplace → sees Alice's share (not his own)
 *   4. Bob initiates Stripe checkout → gets a checkout URL
 *   5. Bob confirms payment (Stripe mocked as paid) → ownership transferred
 *   6. Verify share now belongs to Bob in DB
 *   7. Verify RepositoryAccess granted to Bob
 *   8. Alice un-lists a different share → no longer visible in marketplace
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShareMarketplaceScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private ShareRepository shareRepository;
    @Autowired private RepositoryAccessRepository repositoryAccessRepository;
    @Autowired private StripeSharePurchaseRepository purchaseRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    // Mock Stripe — payment logic tested in isolation; scenario tests the ownership flow
    @MockBean private StripeService stripeService;

    private static final String FAKE_SESSION_ID = "cs_test_scenario_mock";

    private User alice;
    private User bob;
    private Company company;
    private Repository_ repository;
    private Share aliceShare;
    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() throws StripeException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User aliceSeed = new User(
                "alice_mkt_" + suffix, "alice_mkt_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>());
        aliceSeed.setStripeAccountId("acct_test_alice_" + suffix);
        aliceSeed.setPayoutsEnabled(true);   // Alice has finished Stripe Connect onboarding
        alice = userRepository.save(aliceSeed);
        bob = userRepository.save(new User(
                "bob_mkt_" + suffix, "bob_mkt_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));

        aliceJwt = jwtUtil.generateToken(alice);
        bobJwt   = jwtUtil.generateToken(bob);

        Company companySeed = new Company("acme-mkt-" + suffix, alice);
        companySeed.setTotalUnits(1_000_000);
        company = companyRepository.save(companySeed);

        repository = new Repository_();
        repository.setId(UUID.randomUUID());
        repository.setName("acme-mkt-repo-" + suffix);
        repository.setGitUrl("file:///repos/origin/acme-mkt-" + suffix + ".git");
        repository.setCompany(company);
        repository.setOwner(alice);
        repository = repositoryRepository.save(repository);

        // Alice starts with a 30% share (not for sale yet)
        aliceShare = new Share();
        aliceShare.setUser(alice);
        aliceShare.setCompany(company);
        aliceShare.setPercentage(30.0);
        aliceShare.setUnits(300_000);
        aliceShare.setForSale(false);
        aliceShare = shareRepository.save(aliceShare);

        // Mock Stripe to avoid real API calls
        StripeCheckoutResponse fakeCheckout = new StripeCheckoutResponse(FAKE_SESSION_ID, "https://checkout.stripe.com/" + FAKE_SESSION_ID);
        when(stripeService.createCheckoutSession(any(java.math.BigDecimal.class), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(fakeCheckout);

        Session fakeSession = mock(Session.class);
        when(fakeSession.getStatus()).thenReturn("complete");
        when(fakeSession.getPaymentStatus()).thenReturn("paid");
        when(stripeService.retrieveSession(FAKE_SESSION_ID)).thenReturn(fakeSession);
    }

    @AfterEach
    void tearDown() {
        purchaseRepository.findByStripeSessionId(FAKE_SESSION_ID).ifPresent(purchaseRepository::delete);
        repositoryAccessRepository.findByUser(bob).forEach(repositoryAccessRepository::delete);
        shareRepository.findByUser(alice).forEach(shareRepository::delete);
        shareRepository.findByUser(bob).forEach(shareRepository::delete);
        repositoryRepository.delete(repository);
        companyRepository.delete(company);
        userRepository.delete(bob);
        userRepository.delete(alice);
    }

    private HttpHeaders bearer(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Mark for sale → Bob initiates Stripe → confirms → access granted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fullShareLifecycle_markForSale_buyerPurchasesViaStripe_accessGranted() {

        // Step 2: Alice marks her share for sale at $50
        ResponseEntity<Map> markResp = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                Map.class
        );
        assertThat(markResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Share updated = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(updated.isForSale()).isTrue();
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));

        // Step 3: Bob browses the marketplace
        ResponseEntity<List> marketplace = restTemplate.exchange(
                "/api/shares/marketplace",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );
        assertThat(marketplace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(marketplace.getBody()).isNotEmpty();

        // Step 4: Bob initiates Stripe checkout for Alice's share
        String successUrl = "http://localhost/stripe/success";
        String cancelUrl  = "http://localhost/stripe/cancel";
        ResponseEntity<Map> initiateResp = restTemplate.exchange(
                "/api/shares/buy/" + aliceShare.getId() + "/stripe/create"
                        + "?successUrl=" + successUrl + "&cancelUrl=" + cancelUrl,
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                Map.class
        );
        assertThat(initiateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initiateResp.getBody().get("sessionId")).isEqualTo(FAKE_SESSION_ID);

        // Step 5: Bob confirms payment after Stripe redirect
        ResponseEntity<String> confirmResp = restTemplate.exchange(
                "/api/shares/buy/stripe/confirm?sessionId=" + FAKE_SESSION_ID,
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                String.class
        );
        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResp.getBody()).contains("purchased");

        // Step 6: Verify ownership transferred to Bob in DB
        Share transferred = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(transferred.getOwner().getId()).isEqualTo(bob.getId());
        assertThat(transferred.isForSale()).isFalse();

        // Step 7: Verify RepositoryAccess granted to Bob
        Optional<RepositoryAccess> access =
                repositoryAccessRepository.findByUserAndRepository(bob, repository);
        assertThat(access).isPresent();
        assertThat(access.get().getUser().getId()).isEqualTo(bob.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Alice views her own shares → share appears in /my list
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ownerViewsOwnShares_sharesListed() {
        ResponseEntity<List> myShares = restTemplate.exchange(
                "/api/shares/my",
                HttpMethod.GET,
                new HttpEntity<>(bearer(aliceJwt)),
                List.class
        );
        assertThat(myShares.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(myShares.getBody()).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Alice marks share for sale, then un-lists it
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sellerUnmarksShare_disappearsFromMarketplace() {
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        ResponseEntity<String> unmark = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/unmark-for-sale",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                String.class
        );
        assertThat(unmark.getStatusCode()).isEqualTo(HttpStatus.OK);

        Share result = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(result.isForSale()).isFalse();

        ResponseEntity<List> listing = restTemplate.exchange(
                "/api/shares/marketplace",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );
        boolean aliceShareVisible = ((List<?>) listing.getBody()).stream()
                .anyMatch(s -> aliceShare.getId().toString()
                        .equals(((Map<?, ?>) s).get("id")));
        assertThat(aliceShareVisible).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Alice's share excluded from her own marketplace view
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sellerDoesNotSeeOwnShareInMarketplace() {
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        ResponseEntity<List> marketplace = restTemplate.exchange(
                "/api/shares/marketplace",
                HttpMethod.GET,
                new HttpEntity<>(bearer(aliceJwt)),
                List.class
        );
        assertThat(marketplace.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean ownShareVisible = ((List<?>) marketplace.getBody()).stream()
                .anyMatch(s -> aliceShare.getId().toString()
                        .equals(((Map<?, ?>) s).get("id")));
        assertThat(ownShareVisible).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Unauthenticated user cannot initiate a share purchase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_cannotInitiateSharePurchase() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/shares/buy/" + aliceShare.getId()
                        + "/stripe/create?successUrl=http://x&cancelUrl=http://y",
                null, String.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Owner cannot buy their own share → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ownerCannotBuyOwnShare_returns400() {
        // Alice marks her share for sale
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        // Alice tries to buy it herself
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/shares/buy/" + aliceShare.getId()
                        + "/stripe/create?successUrl=http://localhost/success&cancelUrl=http://localhost/cancel",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Confirming the same Stripe session twice → idempotent 200
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void doubleConfirm_isIdempotent() {
        // Alice marks for sale, Bob initiates and confirms once
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        restTemplate.exchange(
                "/api/shares/buy/" + aliceShare.getId()
                        + "/stripe/create?successUrl=http://localhost/success&cancelUrl=http://localhost/cancel",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), Map.class);

        restTemplate.exchange(
                "/api/shares/buy/stripe/confirm?sessionId=" + FAKE_SESSION_ID,
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);

        // Bob attempts to confirm the same session a second time
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/shares/buy/stripe/confirm?sessionId=" + FAKE_SESSION_ID,
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                String.class);

        // e.g. the webhook completed it first — the second confirm must not fail or transfer again
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shareRepository.findById(aliceShare.getId()).orElseThrow().getOwner().getId()).isEqualTo(bob.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security regression: only the owner can list / split / unlist a share
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void nonOwner_cannotListSplitOrUnlistSomeoneElsesShare() {
        ResponseEntity<String> mark = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=0.50",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);
        assertThat(mark.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> split = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/split?percentage=1",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);
        assertThat(split.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Share unchanged = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(unchanged.isForSale()).isFalse();
        assertThat(unchanged.getPercentage()).isEqualTo(aliceShare.getPercentage());

        // Alice lists it; Bob still can't take it off the market
        restTemplate.exchange("/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), String.class);
        ResponseEntity<String> unmark = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/unmark-for-sale",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);
        assertThat(unmark.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(shareRepository.findById(aliceShare.getId()).orElseThrow().isForSale()).isTrue();
    }

    @Test
    void checkoutUrls_areBuiltServerSide_notTakenFromRequest() throws StripeException {
        restTemplate.exchange("/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), String.class);

        restTemplate.exchange("/api/shares/buy/" + aliceShare.getId()
                        + "/stripe/create?successUrl=https://evil.example&cancelUrl=https://evil.example",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);

        org.mockito.Mockito.verify(stripeService).createCheckoutSession(
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("50.00")),
                org.mockito.ArgumentMatchers.eq(alice.getStripeAccountId()),
                org.mockito.ArgumentMatchers.eq("http://localhost:5173/stripe/success"),
                org.mockito.ArgumentMatchers.eq("http://localhost:5173/stripe/cancel"),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
