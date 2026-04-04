package com.thethirdlicense.controllers;

import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Repository_;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.CompanyService;
import com.thethirdlicense.services.GitService;
import com.thethirdlicense.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock private CompanyService companyService;
    @Mock private UserRepository userRepository;
    @Mock private RepositoryRepository repositoryRepository;
    @Mock private ContributionRepository contributionRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private GitService gitService;
    @Mock private UserService userService;
    @Mock private RepositoryAccessRepository repositoryAccessRepository;
    @Mock private AccessRequestRepository accessRequestRepository;

    @InjectMocks
    private CompanyController controller;

    private User owner;
    private UserPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        owner = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        owner.setId(UUID.randomUUID());
        ownerPrincipal = new UserPrincipal(owner.getId(), "alice", "hashed", "alice@test.com", Collections.emptyList());
    }

    // ── helper: set up SecurityContextHolder with a given principal ───────────

    private void mockSecurityContext(UserPrincipal principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private void mockAnonymousContext() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ── Scenario: Company owner creates a new company ─────────────────────────

    @Test
    void openCompany_authenticatedOwner_returns200WithRepoUrl() throws Exception {
        mockSecurityContext(ownerPrincipal);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));

        Company company = new Company("Acme Inc", owner);

        Repository_ repo = new Repository_();
        repo.setId(UUID.randomUUID());
        repo.setGitUrl("http://localhost:9090/alice/acme.git");

        when(companyService.openCompany(any(Company.class), eq(owner))).thenAnswer(inv -> company);
        when(repositoryRepository.findByCompany(company)).thenReturn(Optional.of(repo));

        Company requestBody = new Company();
        requestBody.setName("Acme Inc");

        ResponseEntity<?> response = controller.openCompany(requestBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("http://localhost:9090/alice/acme.git");
    }

    // ── Scenario: Unauthenticated user tries to open a company ───────────────

    @Test
    void openCompany_notAuthenticated_returns500WithError() {
        mockAnonymousContext();

        ResponseEntity<?> response = controller.openCompany(new Company());

        // The controller catches UnauthorizedException and returns 500
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Scenario: User fetches their own companies ────────────────────────────

    @Test
    void getMyCompanies_authenticatedUser_returnsOwnedAndContributedCompanies() {
        mockSecurityContext(ownerPrincipal);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));

        Company owned = new Company("Acme Inc", owner);

        when(companyRepository.findByOwner(owner)).thenReturn(List.of(owned));
        when(companyRepository.findByUsers(owner)).thenReturn(Collections.emptyList());
        when(companyRepository.findByRepositoryAccessUser(owner)).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.getMyCompanies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> companies = (List<?>) body.get("companies");
        assertThat(companies).hasSize(1);
    }

    // ── Scenario: Contributor appears in my-companies via repo access ─────────

    @Test
    void getMyCompanies_contributorWithRepoAccess_returnsAccessedCompanies() {
        mockSecurityContext(ownerPrincipal);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));

        User otherOwner = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        otherOwner.setId(UUID.randomUUID());

        Company accessedCompany = new Company("Bob Corp", otherOwner);

        when(companyRepository.findByOwner(owner)).thenReturn(Collections.emptyList());
        when(companyRepository.findByUsers(owner)).thenReturn(Collections.emptyList());
        when(companyRepository.findByRepositoryAccessUser(owner)).thenReturn(List.of(accessedCompany));

        ResponseEntity<?> response = controller.getMyCompanies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> companies = (List<?>) body.get("companies");
        assertThat(companies).hasSize(1);
        Map<?, ?> company = (Map<?, ?>) companies.get(0);
        assertThat(company.get("name")).isEqualTo("Bob Corp");
    }

    // ── Scenario: User with no companies sees empty list ─────────────────────

    @Test
    void getMyCompanies_userWithNoCompanies_returnsEmptyList() {
        mockSecurityContext(ownerPrincipal);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(companyRepository.findByOwner(owner)).thenReturn(Collections.emptyList());
        when(companyRepository.findByUsers(owner)).thenReturn(Collections.emptyList());
        when(companyRepository.findByRepositoryAccessUser(owner)).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.getMyCompanies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> companies = (List<?>) body.get("companies");
        assertThat(companies).isEmpty();
    }

    // ── Scenario: Anyone can list all companies ───────────────────────────────

    @Test
    void getAllCompanies_returns200WithAllCompanies() {
        User bob = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        bob.setId(UUID.randomUUID());

        Company c1 = new Company("Alpha", owner);
        Company c2 = new Company("Beta", bob);

        when(companyRepository.findAll()).thenReturn(List.of(c1, c2));

        ResponseEntity<?> response = controller.getAllCompanies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("name")).isIn("Alpha", "Beta");
    }
}
