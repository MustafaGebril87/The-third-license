package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.responses.AuthResponse;
import com.thethirdlicense.security.JWTUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario: Company access request → approval workflow
 *
 * Cast of characters:
 *   - Alice (owner): creates a company + repository (seeded directly in DB)
 *   - Bob (contributor): registers, requests access, gets approved
 *
 * Steps:
 *   1. Seed Alice's company and repository (bypass Git fs operations)
 *   2. Bob registers + logs in
 *   3. Bob requests access to Alice's repository
 *   4. Alice views pending access requests → sees Bob's request
 *   5. Alice approves Bob's request
 *   6. Verify RepositoryAccess record created for Bob in DB
 *   7. Bob calls /my-companies → Alice's company is now listed
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyAccessScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private AccessRequestRepository accessRequestRepository;
    @Autowired private RepositoryAccessRepository repositoryAccessRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private User alice;
    private User bob;
    private Company company;
    private Repository_ repository;
    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Seed Alice (owner) directly in DB
        alice = new User("alice_" + suffix, "alice_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>());
        alice = userRepository.save(alice);
        aliceJwt = jwtUtil.generateToken(alice);

        // Seed Bob (contributor) directly in DB
        bob = new User("bob_" + suffix, "bob_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>());
        bob = userRepository.save(bob);
        bobJwt = jwtUtil.generateToken(bob);

        // Seed company and repository (bypass Git filesystem)
        company = companyRepository.save(new Company("acme-" + suffix, alice));

        repository = new Repository_();
        repository.setId(UUID.randomUUID());
        repository.setName("acme-repo-" + suffix);
        repository.setGitUrl("file:///repos/origin/acme-" + suffix + ".git");
        repository.setCompany(company);
        repository.setOwner(alice);
        repository = repositoryRepository.save(repository);
    }

    @AfterEach
    void tearDown() {
        repositoryAccessRepository.findByUser(bob).forEach(repositoryAccessRepository::delete);
        accessRequestRepository.findAll().stream()
                .filter(ar -> ar.getUser().getId().equals(bob.getId()))
                .forEach(accessRequestRepository::delete);
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
    // Scenario: Bob requests access → Alice approves → Bob sees company
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void contributorRequestsAccess_ownerApproves_contributorSeesCompany() {

        // Step 3: Bob requests access to Alice's repository
        ResponseEntity<Map> accessReq = restTemplate.exchange(
                "/api/contributions/" + repository.getId() + "/request-access",
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                Map.class
        );
        assertThat(accessReq.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accessReq.getBody().get("message").toString()).contains("sent");

        // Verify AccessRequest persisted in DB
        List<AccessRequest> pending = accessRequestRepository.findPendingByCompanyOwner(alice);
        assertThat(pending).hasSize(1);
        UUID requestId = pending.get(0).getId();
        assertThat(pending.get(0).getUser().getId()).isEqualTo(bob.getId());
        assertThat(pending.get(0).getStatus()).isEqualTo(AccessRequest.Status.PENDING);

        // Step 4: Alice views pending requests via HTTP
        ResponseEntity<List> pendingResp = restTemplate.exchange(
                "/api/contributions/pending_requests",
                HttpMethod.GET,
                new HttpEntity<>(bearer(aliceJwt)),
                List.class
        );
        assertThat(pendingResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendingResp.getBody()).hasSize(1);

        // Step 5: Alice approves Bob's request
        ResponseEntity<String> approveResp = restTemplate.exchange(
                "/api/contributions/requests/" + requestId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                String.class
        );
        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResp.getBody()).contains("approved");

        // Step 6: Verify RepositoryAccess record created for Bob in DB
        Optional<RepositoryAccess> access =
                repositoryAccessRepository.findByUserAndRepository(bob, repository);
        assertThat(access).isPresent();
        assertThat(access.get().getUser().getId()).isEqualTo(bob.getId());

        // Verify AccessRequest status is now APPROVED in DB
        AccessRequest updatedRequest = accessRequestRepository.findById(requestId).orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AccessRequest.Status.APPROVED);

        // Step 7: Bob calls /my-companies → Alice's company appears
        ResponseEntity<Map> myCompanies = restTemplate.exchange(
                "/api/companies/my-companies",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                Map.class
        );
        assertThat(myCompanies.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> companies = (List<?>) myCompanies.getBody().get("companies");
        assertThat(companies).isNotEmpty();
        boolean found = companies.stream()
                .anyMatch(c -> ((Map<?, ?>) c).get("name").equals(company.getName()));
        assertThat(found).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Bob tries to request access a second time → 400 (already granted)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void contributorRequestsAccessTwice_secondRequestRejected() {
        // First request
        restTemplate.exchange(
                "/api/contributions/" + repository.getId() + "/request-access",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), Map.class);

        // Approve first
        UUID requestId = accessRequestRepository.findPendingByCompanyOwner(alice).get(0).getId();
        restTemplate.exchange(
                "/api/contributions/requests/" + requestId + "/approve",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), String.class);

        // Second request — should be rejected
        ResponseEntity<String> secondReq = restTemplate.exchange(
                "/api/contributions/" + repository.getId() + "/request-access",
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                String.class
        );
        assertThat(secondReq.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Alice declines Bob's request → no RepositoryAccess created
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ownerDeclinesRequest_noAccessGranted() {
        restTemplate.exchange(
                "/api/contributions/" + repository.getId() + "/request-access",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), Map.class);

        UUID requestId = accessRequestRepository.findPendingByCompanyOwner(alice).get(0).getId();

        ResponseEntity<String> decline = restTemplate.exchange(
                "/api/contributions/requests/" + requestId + "/decline",
                HttpMethod.POST,
                new HttpEntity<>(bearer(aliceJwt)),
                String.class
        );
        assertThat(decline.getStatusCode()).isEqualTo(HttpStatus.OK);

        // No RepositoryAccess should be created
        Optional<RepositoryAccess> access =
                repositoryAccessRepository.findByUserAndRepository(bob, repository);
        assertThat(access).isEmpty();

        // Request status is DECLINED
        AccessRequest updatedRequest = accessRequestRepository.findById(requestId).orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AccessRequest.Status.DECLINED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Unauthenticated user cannot request access
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_cannotRequestAccess() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/contributions/" + repository.getId() + "/request-access",
                null, String.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
