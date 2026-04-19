package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
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
 * Scenario: Company search engine
 *
 * Cast of characters:
 *   - Alice (owner): runs "AlphaCorp" (has a repository) and "Alpha Design" (no repository)
 *   - Bob (searcher): wants to discover companies and request access
 *   - Carol (owner): runs "BetaWorks" — should NOT appear in Alice-specific searches
 *
 * Scenarios:
 *   1. Bob searches by partial name → finds only Alice's matching companies
 *   2. Bob searches case-insensitively → still finds Alice's company
 *   3. Bob uses the repositoryId from search results to request access in one flow
 *   4. Bob searches for a company with no repository → result omits repositoryId
 *   5. Searching a term that matches nobody → empty list
 *   6. Unauthenticated user cannot use the search endpoint
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanySearchScenarioTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private AccessRequestRepository accessRequestRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;
    private User alice;
    private User bob;
    private User carol;
    private Company alphaCorp;      // Alice's company — has a repository
    private Company alphaDesign;    // Alice's company — no repository
    private Company betaWorks;      // Carol's company — should not match "alpha" queries
    private Repository_ alphaRepo;
    private String aliceJwt;
    private String bobJwt;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        alice = userRepository.save(new User(
                "alice_srch_" + suffix, "alice_srch_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));
        bob = userRepository.save(new User(
                "bob_srch_" + suffix, "bob_srch_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));
        carol = userRepository.save(new User(
                "carol_srch_" + suffix, "carol_srch_" + suffix + "@test.com",
                passwordEncoder.encode("strongpass1"), new HashSet<>()));

        aliceJwt = jwtUtil.generateToken(alice);
        bobJwt   = jwtUtil.generateToken(bob);

        alphaCorp   = companyRepository.save(new Company("Alpha-Corp-" + suffix, alice));
        alphaDesign = companyRepository.save(new Company("Alpha-Design-" + suffix, alice));
        betaWorks   = companyRepository.save(new Company("Beta-Works-" + suffix, carol));

        // Only AlphaCorp gets a repository
        alphaRepo = new Repository_();
        alphaRepo.setId(UUID.randomUUID());
        alphaRepo.setName("alpha-repo-" + suffix);
        alphaRepo.setGitUrl("file:///repos/origin/alpha-" + suffix + ".git");
        alphaRepo.setCompany(alphaCorp);
        alphaRepo.setOwner(alice);
        alphaRepo = repositoryRepository.save(alphaRepo);
    }

    @AfterEach
    void tearDown() {
        accessRequestRepository.findAll().stream()
                .filter(ar -> ar.getUser().getId().equals(bob.getId()))
                .forEach(accessRequestRepository::delete);
        repositoryRepository.delete(alphaRepo);
        companyRepository.deleteAll(List.of(alphaCorp, alphaDesign, betaWorks));
        userRepository.deleteAll(List.of(alice, bob, carol));
    }

    private HttpHeaders bearer(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1: Bob discovers companies and requests access in one flow
    //
    //   1. Bob searches "AlphaCorp-{suffix}" → gets 1 result with repositoryId
    //   2. Bob uses that repositoryId to request access immediately
    //   3. AccessRequest is created for Bob in DB
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void bob_searchesCompany_thenRequestsAccessUsingRepositoryIdFromResult() {

        // Step 1: Bob searches for AlphaCorp by name
        ResponseEntity<List> searchResp = restTemplate.exchange(
                "/api/companies/search?q=Alpha-Corp-" + suffix,
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );
        assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) searchResp.getBody();
        assertThat(results).hasSize(1);

        Map<String, Object> hit = results.get(0);
        assertThat(hit.get("name")).isEqualTo(alphaCorp.getName());
        assertThat(hit.get("owner")).isEqualTo(alice.getUsername());
        assertThat(hit).containsKey("repositoryId");

        // Step 2: Bob uses the repositoryId from the search result to request access
        String repositoryId = hit.get("repositoryId").toString();
        ResponseEntity<Map> accessResp = restTemplate.exchange(
                "/api/contributions/" + repositoryId + "/request-access",
                HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)),
                Map.class
        );
        assertThat(accessResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accessResp.getBody().get("message").toString()).containsIgnoringCase("sent");

        // Step 3: Verify AccessRequest is persisted in DB for Bob
        List<AccessRequest> pending = accessRequestRepository.findPendingByCompanyOwner(alice);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getUser().getId()).isEqualTo(bob.getId());
        assertThat(pending.get(0).getRepository().getId()).isEqualTo(alphaRepo.getId());
        assertThat(pending.get(0).getStatus()).isEqualTo(AccessRequest.Status.PENDING);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2: Partial name search returns only matching companies
    //
    //   "Alpha-" matches "Alpha-Corp-{suffix}" and "Alpha-Design-{suffix}" but NOT "Beta-Works-{suffix}"
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_partialName_returnsOnlyMatchingCompanies() {
        // "Alpha-" is a prefix shared by both seeded Alpha companies but not BetaWorks
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/companies/search?q=Alpha-",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody();

        // Filter to only our seeded companies in case of residual DB state
        Set<String> matchedNames = new HashSet<>();
        results.forEach(r -> matchedNames.add((String) r.get("name")));

        assertThat(matchedNames).contains(alphaCorp.getName(), alphaDesign.getName());
        assertThat(matchedNames).doesNotContain(betaWorks.getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3: Case-insensitive search
    //
    //   "ALPHA-CORP-{suffix}" (uppercase) still finds "Alpha-Corp-{suffix}"
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_uppercaseQuery_findsMixedCaseCompanyName() {
        String upperQuery = ("Alpha-Corp-" + suffix).toUpperCase();

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/companies/search?q=" + upperQuery,
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("name")).isEqualTo(alphaCorp.getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 4: Company with no repository — search still returns it,
    //             but repositoryId is absent (no NPE, no 500)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_companyWithNoRepository_returnsResultWithoutRepositoryId() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/companies/search?q=Alpha-Design-" + suffix,
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("name")).isEqualTo(alphaDesign.getName());
        assertThat(results.get(0)).doesNotContainKey("repositoryId");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 5: Query with no matches returns empty list (not 404 / 500)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_queryMatchesNoCompany_returnsEmptyList() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/companies/search?q=XyzNoSuchCompany999",
                HttpMethod.GET,
                new HttpEntity<>(bearer(bobJwt)),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 6: Unauthenticated user cannot use the search endpoint
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/companies/search?q=alpha",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 7: Owner's own company appears in search results too
    //
    //   The search is global — Alice searching for her own company finds it.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void search_ownerCanFindOwnCompany() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/companies/search?q=Alpha-Corp-" + suffix,
                HttpMethod.GET,
                new HttpEntity<>(bearer(aliceJwt)),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("owner")).isEqualTo(alice.getUsername());
    }
}
