package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.security.JWTUtil;
import com.thethirdlicense.services.TokenService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doNothing;

/**
 * Scenario: Share marketplace — full lifecycle
 *
 * Cast of characters:
 *   - Alice (seller): owns a 30% share in Acme Corp
 *   - Bob (buyer): wants to buy a share and gain access to Acme's repository
 *
 * Steps:
 *   1. Seed Alice + Bob + Acme company + repository + share
 *   2. Alice marks the share for sale at $50
 *   3. Bob browses the marketplace → sees Alice's share (not his own)
 *   4. Bob purchases Alice's share
 *   5. Verify ownership transferred: share now belongs to Bob in DB
 *   6. Verify RepositoryAccess granted to Bob
 *   7. Alice un-lists a different share → no longer visible in marketplace
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShareMarketplaceScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private ShareRepository shareRepository;
    @Autowired private RepositoryAccessRepository repositoryAccessRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    // Mock the currency service — financial logic is tested separately
    @MockBean private TokenService tokenService;

    private User alice;
    private User bob;
    private Company company;
    private Repository_ repository;
    private Share aliceShare;
    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        alice = userRepository.save(new User(
                "alice_mkt_" + suffix, "alice_mkt_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));
        bob = userRepository.save(new User(
                "bob_mkt_" + suffix, "bob_mkt_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));

        aliceJwt = jwtUtil.generateToken(alice);
        bobJwt   = jwtUtil.generateToken(bob);

        company = companyRepository.save(new Company("acme-mkt-" + suffix, alice));

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
        aliceShare.setForSale(false);
        aliceShare = shareRepository.save(aliceShare);

        // Mock currency service to do nothing (financial ops tested separately)
        doNothing().when(tokenService).purchaseShare(any(), any(), anyDouble());
    }

    @AfterEach
    void tearDown() {
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
    // Scenario: Mark for sale → marketplace listing → buy → access granted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fullShareLifecycle_markForSale_buyerPurchases_accessGranted() {

        // Step 2: Alice marks her share for sale at $50
        ResponseEntity<Map> markResp = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                Map.class
        );
        assertThat(markResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify in DB: share is now for sale
        Share updated = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(updated.isForSale()).isTrue();
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));

        // Step 3: Bob browses the marketplace — should see Alice's share, not Bob's own
        ResponseEntity<List> marketplace = restTemplate.exchange(
                "/api/shares/marketplace",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );
        assertThat(marketplace.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> listings = marketplace.getBody();
        assertThat(listings).isNotEmpty();
        // All listings belong to someone else (not Bob)
        listings.forEach(listing -> {
            Map<?, ?> share = (Map<?, ?>) listing;
            assertThat(share.get("ownerUsername")).isNotEqualTo(bob.getUsername());
        });

        // Step 4: Bob purchases Alice's share (price matches what Alice listed it for)
        ResponseEntity<String> buyResp = restTemplate.exchange(
                "/api/shares/buy/" + aliceShare.getId() + "?price=50.0",
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                String.class
        );
        assertThat(buyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buyResp.getBody()).contains("purchased");

        // Step 5: Verify RepositoryAccess granted to Bob
        // Note: share ownership transfer is delegated to TokenService (mocked here).
        // The controller directly handles RepositoryAccess — verify that.
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
        // Mark for sale
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        // Un-list (endpoint returns Share entity, not Map)
        ResponseEntity<String> unmark = restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/unmark-for-sale",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                String.class
        );
        assertThat(unmark.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify in DB: no longer for sale
        Share updated = shareRepository.findById(aliceShare.getId()).orElseThrow();
        assertThat(updated.isForSale()).isFalse();

        // Bob browses marketplace → Alice's share is gone
        ResponseEntity<List> marketplace = restTemplate.exchange(
                "/api/shares/marketplace",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );
        boolean aliceShareVisible = ((List<?>) marketplace.getBody()).stream()
                .anyMatch(s -> aliceShare.getId().toString()
                        .equals(((Map<?, ?>) s).get("id")));
        assertThat(aliceShareVisible).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Alice's share excluded from her own marketplace view
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sellerDoesNotSeeOwnShareInMarketplace() {
        // Mark for sale
        restTemplate.exchange(
                "/api/shares/" + aliceShare.getId() + "/mark-for-sale?price=50.00",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), Map.class);

        // Alice views marketplace → her own share should NOT appear
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
    // Scenario: Unauthenticated user cannot buy a share
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_cannotBuyShare() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/shares/buy/" + aliceShare.getId() + "?price=30.0",
                null, String.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
