package com.thethirdlicense.integration;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.RepositoryRepository;
import com.thethirdlicense.repositories.UserRepository;
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
 * Integration tests for GET /api/companies/search?q=...
 *
 * Seeded data per test run (unique suffix per @BeforeEach):
 *   - AlphaCorp-{suffix}   → has a repository
 *   - alpha-studios-{suffix} → no repository
 *   - BetaLtd-{suffix}     → no repository
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanySearchIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;
    private User testUser;
    private String jwtToken;
    private Company alphaCompany;
    private Company alphaStudios;
    private Company betaCompany;
    private Repository_ alphaRepo;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        testUser = new User(
            "searcher_" + suffix,
            "searcher_" + suffix + "@test.com",
            passwordEncoder.encode("pass1234"),
            new HashSet<>()
        );
        testUser = userRepository.save(testUser);
        jwtToken = jwtUtil.generateToken(testUser);

        alphaCompany  = companyRepository.save(new Company("AlphaCorp-" + suffix, testUser));
        alphaStudios  = companyRepository.save(new Company("alpha-studios-" + suffix, testUser));
        betaCompany   = companyRepository.save(new Company("BetaLtd-" + suffix, testUser));

        alphaRepo = new Repository_();
        alphaRepo.setId(UUID.randomUUID());
        alphaRepo.setName("alpha-repo-" + suffix);
        alphaRepo.setGitUrl("file:///repos/origin/alpha-" + suffix + ".git");
        alphaRepo.setCompany(alphaCompany);
        alphaRepo.setOwner(testUser);
        alphaRepo = repositoryRepository.save(alphaRepo);
    }

    @AfterEach
    void tearDown() {
        repositoryRepository.delete(alphaRepo);
        companyRepository.deleteAll(List.of(alphaCompany, alphaStudios, betaCompany));
        userRepository.delete(testUser);
    }

    private HttpHeaders bearer() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        return h;
    }

    // ── partial match returns only the matching company ──────────────────────

    @Test
    void search_partialSubstring_matchesOnlyOneCompany() {
        // "Corp-{suffix}" is a substring of "AlphaCorp-{suffix}" but not of the other two
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=Corp-" + suffix,
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("name")).isEqualTo(alphaCompany.getName());
    }

    @Test
    void search_partialSuffix_returnsAllThreeCompanies() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=" + suffix,
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        Set<String> names = new HashSet<>();
        body.forEach(m -> names.add((String) m.get("name")));
        assertThat(names).containsExactlyInAnyOrder(
            alphaCompany.getName(), alphaStudios.getName(), betaCompany.getName()
        );
    }

    // ── case-insensitive match ────────────────────────────────────────────────

    @Test
    void search_uppercaseQuery_matchesLowercaseCompanyName() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=ALPHA-STUDIOS-" + suffix.toUpperCase(),
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("name")).isEqualTo(alphaStudios.getName());
    }

    // ── no match returns empty list ───────────────────────────────────────────

    @Test
    void search_noMatch_returnsEmptyList() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=ZzzNoMatchXyz999",
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ── repositoryId included only when repo exists ───────────────────────────

    @Test
    void search_companyWithRepo_includesRepositoryId() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=" + alphaCompany.getName(),
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).containsKey("repositoryId");
        assertThat(body.get(0).get("repositoryId").toString())
            .isEqualTo(alphaRepo.getId().toString());
    }

    @Test
    void search_companyWithoutRepo_omitsRepositoryId() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=" + betaCompany.getName(),
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).doesNotContainKey("repositoryId");
    }

    // ── response shape ────────────────────────────────────────────────────────

    @Test
    void search_responseIncludesIdNameAndOwner() {
        ResponseEntity<List> response = restTemplate.exchange(
            "/api/companies/search?q=" + betaCompany.getName(),
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> entry = ((List<Map<String, Object>>) response.getBody()).get(0);
        assertThat(entry).containsKeys("id", "name", "owner");
        assertThat(entry.get("name")).isEqualTo(betaCompany.getName());
        assertThat(entry.get("owner")).isEqualTo(testUser.getUsername());
    }

    // ── authentication required ───────────────────────────────────────────────

    @Test
    void search_unauthenticated_returns4xx() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/companies/search?q=alpha",
            String.class
        );
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
