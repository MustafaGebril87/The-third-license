package com.thethirdlicense.controllers;

import com.thethirdlicense.exceptions.UnauthorizedException;
import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.security.UserPrincipal;
import com.thethirdlicense.services.ContributionService;
import com.thethirdlicense.services.ShareService;
import com.thethirdlicense.services.TokenService;
import com.thethirdlicense.services.UserService;
import com.thethirdlicense.Util.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionControllerTest {

    @Mock private ContributionService contributionService;
    @Mock private RepositoryRepository repositoryRepository;
    @Mock private ContributionRepository contributionRepository;
    @Mock private ShareService shareService;
    @Mock private AccessRequestRepository accessRequestRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private TokenService currencyService;
    @Mock private ApplicationProperties applicationProperties;
    @Mock private MergeRequestRepository mergeRequestRepository;
    @Mock private RepositoryAccessRepository repositoryAccessRepository;
    @Mock private UserService userService;

    @InjectMocks
    private ContributionController controller;

    private User contributor;
    private User owner;
    private UserPrincipal contributorPrincipal;
    private UserPrincipal ownerPrincipal;
    private Repository_ repository;
    private Company company;

    @BeforeEach
    void setUp() {
        contributor = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        contributor.setId(UUID.randomUUID());

        owner = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        owner.setId(UUID.randomUUID());

        contributorPrincipal = new UserPrincipal(contributor.getId(), "bob", "hashed", "bob@test.com", Collections.emptyList());
        ownerPrincipal = new UserPrincipal(owner.getId(), "alice", "hashed", "alice@test.com", Collections.emptyList());

        company = new Company("Acme Inc", owner);

        repository = new Repository_();
        repository.setId(UUID.randomUUID());
        repository.setName("acme-repo");
        repository.setCompany(company);
        repository.setOwner(owner);

        // UserService is @Autowired field-injected in the controller (not constructor-injected),
        // so Mockito's constructor injection misses it — inject manually.
        ReflectionTestUtils.setField(controller, "userService", userService);
    }

    private void mockSecurityContext(UserPrincipal principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ── Scenario: Contributor requests access to a repository ─────────────────

    @Test
    void requestAccess_newRequest_returns200WithMessage() {
        mockSecurityContext(contributorPrincipal);
        when(userService.findById(contributor.getId())).thenReturn(contributor);
        when(accessRequestRepository.existsByRepositoryIdAndUserIdAndStatus(
            repository.getId(), contributor.getId(), AccessRequest.Status.APPROVED))
            .thenReturn(false);
        when(contributionService.requestAccess(repository.getId(), contributor))
            .thenReturn("Access request submitted.");

        ResponseEntity<?> response = controller.requestAccess(repository.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Access request submitted.");
    }

    // ── Scenario: Contributor tries to request access they already have ────────

    @Test
    void requestAccess_alreadyGranted_returns400() {
        mockSecurityContext(contributorPrincipal);
        when(userService.findById(contributor.getId())).thenReturn(contributor);
        when(accessRequestRepository.existsByRepositoryIdAndUserIdAndStatus(
            repository.getId(), contributor.getId(), AccessRequest.Status.APPROVED))
            .thenReturn(true);

        ResponseEntity<?> response = controller.requestAccess(repository.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("Access already granted");
        verify(contributionService, never()).requestAccess(any(), any());
    }

    // ── Scenario: Owner views pending access requests ─────────────────────────

    @Test
    void getPendingRequests_owner_returnsAccessRequestDtos() {
        mockSecurityContext(ownerPrincipal);
        when(userService.findById(owner.getId())).thenReturn(owner);

        AccessRequestDto dto = new AccessRequestDto(UUID.randomUUID(), AccessRequest.Status.PENDING, repository.getId());
        when(contributionService.findPendingRequestsByOwner(owner)).thenReturn(List.of(dto));

        ResponseEntity<?> response = controller.getPendingRequests();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> body = (List<?>) response.getBody();
        assertThat(body).hasSize(1);
    }

    // ── Scenario: Owner approves an access request ────────────────────────────

    @Test
    void approveRequest_validOwner_grantsAccessAndReturns200() {
        mockSecurityContext(ownerPrincipal);
        when(userService.findById(owner.getId())).thenReturn(owner);

        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setUser(contributor);
        accessRequest.setRepository(repository);
        accessRequest.setApproved(AccessRequest.Status.PENDING);

        UUID requestId = UUID.randomUUID();
        when(accessRequestRepository.findById(requestId)).thenReturn(Optional.of(accessRequest));
        when(repositoryAccessRepository.findByUserAndRepository(contributor, repository))
            .thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.approveRequest(requestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Access request approved.");
        verify(repositoryAccessRepository).save(any(RepositoryAccess.class));
    }

    // ── Scenario: Non-owner tries to approve a request ────────────────────────

    @Test
    void approveRequest_nonOwner_throwsUnauthorizedException() {
        mockSecurityContext(contributorPrincipal);
        when(userService.findById(contributor.getId())).thenReturn(contributor);

        // The access request is for a repo owned by alice; bob is trying to approve
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setUser(owner); // alice requested access (scenario: owner accessing bob's repo)
        accessRequest.setRepository(repository); // but repo's company owner is alice
        // company.getOwner() == alice; logged-in user is bob → mismatch → throw

        UUID requestId = UUID.randomUUID();
        when(accessRequestRepository.findById(requestId)).thenReturn(Optional.of(accessRequest));

        assertThrows(UnauthorizedException.class,
            () -> controller.approveRequest(requestId));
    }

    // ── Scenario: Owner declines an access request ────────────────────────────

    @Test
    void declineRequest_validOwner_returns200() {
        mockSecurityContext(ownerPrincipal);
        when(userService.findById(owner.getId())).thenReturn(owner);
        doNothing().when(contributionService).declineRequest(any(), eq(owner));

        UUID requestId = UUID.randomUUID();
        ResponseEntity<String> response = controller.declineRequest(requestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Access request declined.");
    }

    // ── Scenario: Contributor views their own contributions ───────────────────

    @Test
    void getMyContributions_returnsContributorDtos() {
        mockSecurityContext(contributorPrincipal);
        when(userService.findById(contributor.getId())).thenReturn(contributor);

        // Build a minimal Contribution so ContributionDto::new doesn't NPE
        Contribution contribution = new Contribution();
        contribution.setBranch("feature-1");
        contribution.setRepository(repository);
        contribution.setUser(contributor);

        when(contributionService.findByUser(contributor)).thenReturn(List.of(contribution));

        ResponseEntity<?> response = controller.getMyContributions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> body = (List<?>) response.getBody();
        assertThat(body).hasSize(1);
    }

    // ── Scenario: Owner views pending contributions ───────────────────────────

    @Test
    void getPendingContributions_owner_returnsPendingList() {
        mockSecurityContext(ownerPrincipal);
        when(userService.findById(owner.getId())).thenReturn(owner);

        // contributionService.findPendingByCompanyOwner returns List<ContributionDto>
        Contribution contribution = new Contribution();
        contribution.setBranch("feature-1");
        contribution.setRepository(repository);
        contribution.setUser(contributor);
        ContributionDto dto = new ContributionDto(contribution);

        when(contributionService.findPendingByCompanyOwner(owner)).thenReturn(List.of(dto));

        ResponseEntity<?> response = controller.getPendingContributions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> body = (List<?>) response.getBody();
        assertThat(body).hasSize(1);
    }

    // ── Scenario: Owner declines a contribution ───────────────────────────────

    @Test
    void declineContribution_validOwner_returns200() {
        mockSecurityContext(ownerPrincipal);
        when(userService.findById(owner.getId())).thenReturn(owner);
        doNothing().when(contributionService).declineContribution(any(), eq(owner));

        UUID contributionId = UUID.randomUUID();
        ResponseEntity<String> response = controller.declineContribution(contributionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Contribution declined.");
    }

    // ── Scenario: Unauthenticated user cannot view pending contributions ───────

    @Test
    void getPendingContributions_unauthenticated_throwsUnauthorizedException() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        assertThrows(UnauthorizedException.class, () -> controller.getPendingContributions());
    }
}
