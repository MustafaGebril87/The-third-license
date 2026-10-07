package com.thethirdlicense.services;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.models.User;
import com.thethirdlicense.repositories.ShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock private ShareRepository shareRepository;
    @Mock private com.thethirdlicense.repositories.CompanyRepository companyRepository;

    @InjectMocks
    private ShareService shareService;

    private UUID shareId;
    private User owner;
    private User stranger;
    private Share share;

    @BeforeEach
    void setUp() {
        shareId = UUID.randomUUID();
        owner = new User("alice", "alice@test.com", "hashed", new HashSet<>());
        owner.setId(UUID.randomUUID());
        owner.setStripeAccountId("acct_alice");
        owner.setPayoutsEnabled(true);
        stranger = new User("mallory", "m@test.com", "hashed", new HashSet<>());
        stranger.setId(UUID.randomUUID());

        share = new Share();
        share.setOwner(owner);
        Company company = new Company("Acme", owner);
        company.setTotalUnits(1_000_000);
        share.setCompany(company);
        share.setUnits(400_000);
        share.setPercentage(40.0);
        org.mockito.Mockito.lenient().when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
    }

    @Test
    void markShareForSale_byNonOwner_isDenied() {
        assertThrows(AccessDeniedException.class,
                () -> shareService.markShareForSale(shareId, new BigDecimal("0.50"), stranger.getId()));
        verify(shareRepository, never()).save(any());
        assertThat(share.isForSale()).isFalse();
    }

    @Test
    void unmarkShareForSale_byNonOwner_isDenied() {
        share.setForSale(true);
        assertThrows(AccessDeniedException.class, () -> shareService.unmarkShareForSale(shareId, stranger.getId()));
        assertThat(share.isForSale()).isTrue();
    }

    @Test
    void splitShare_byNonOwner_isDenied() {
        assertThrows(AccessDeniedException.class, () -> shareService.splitShare(shareId, 10.0, stranger.getId()));
        assertThat(share.getPercentage()).isEqualTo(40.0);
    }

    @Test
    void markShareForSale_priceBelowStripeMinimum_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> shareService.markShareForSale(shareId, new BigDecimal("0.10"), owner.getId()));
    }

    @Test
    void markShareForSale_negativePrice_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> shareService.markShareForSale(shareId, new BigDecimal("-5"), owner.getId()));
    }

    @Test
    void markShareForSale_byOwnerWithValidPrice_listsShare() {
        when(shareRepository.save(share)).thenReturn(share);
        Share result = shareService.markShareForSale(shareId, new BigDecimal("25.00"), owner.getId());
        assertThat(result.isForSale()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void splitShare_nanPercentage_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> shareService.splitShare(shareId, Double.NaN, owner.getId()));
        assertThat(share.getPercentage()).isEqualTo(40.0);
    }

    @Test
    void splitShare_whileListedForSale_isRejected() {
        share.setForSale(true);
        assertThrows(IllegalStateException.class, () -> shareService.splitShare(shareId, 10.0, owner.getId()));
    }

    @Test
    void markShareForSale_withoutPayoutsSetUp_isRejected() {
        owner.setPayoutsEnabled(false);
        assertThrows(IllegalStateException.class,
                () -> shareService.markShareForSale(shareId, new BigDecimal("25.00"), owner.getId()));
        assertThat(share.isForSale()).isFalse();
    }

    @Test
    void splitShare_movesUnits_andKeepsCompanyTotal() {
        when(shareRepository.save(any(Share.class))).thenAnswer(inv -> inv.getArgument(0));

        Share split = shareService.splitShare(shareId, 10.0, owner.getId());

        assertThat(split.getUnits()).isEqualTo(100_000);
        assertThat(split.getPercentage()).isEqualTo(10.0);
        assertThat(share.getUnits()).isEqualTo(300_000);
        assertThat(share.getPercentage()).isEqualTo(30.0);
        assertThat(share.getCompany().getTotalUnits()).isEqualTo(1_000_000);
    }

    @Test
    void issueUnits_dilutesEveryoneProportionally() {
        Company company = share.getCompany();
        UUID companyId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(company, "id", companyId);
        Share ownerShare = share; // 400,000 of 1,000,000

        User bob = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        bob.setId(UUID.randomUUID());

        when(companyRepository.findForUpdateById(companyId)).thenReturn(Optional.of(company));
        when(shareRepository.findByUserAndCompany(bob, company)).thenReturn(java.util.List.of());
        when(shareRepository.save(any(Share.class))).thenAnswer(inv -> inv.getArgument(0));
        java.util.List<Share> all = new java.util.ArrayList<>(java.util.List.of(ownerShare));
        when(shareRepository.findByCompany(company)).thenAnswer(inv -> all);

        Share bobShare = shareService.issueUnits(company, bob, 1_000_000);
        all.add(bobShare);
        shareService.syncPercentages(company);

        assertThat(company.getTotalUnits()).isEqualTo(2_000_000);
        assertThat(bobShare.getUnits()).isEqualTo(1_000_000);
        assertThat(bobShare.getPercentage()).isEqualTo(50.0);
        assertThat(ownerShare.getUnits()).isEqualTo(400_000);       // never overwritten
        assertThat(ownerShare.getPercentage()).isEqualTo(20.0);     // diluted 40% -> 20%
    }

    @Test
    void issueForContribution_issues100UnitsPerChangedLine() {
        Company company = share.getCompany();
        UUID companyId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(company, "id", companyId);
        User bob = new User("bob", "bob@test.com", "hashed", new HashSet<>());
        bob.setId(UUID.randomUUID());

        com.thethirdlicense.models.Repository_ repo = new com.thethirdlicense.models.Repository_();
        repo.setCompany(company);
        com.thethirdlicense.models.Contribution c = new com.thethirdlicense.models.Contribution();
        c.setUser(bob);
        c.setRepository(repo);
        c.setModifiedCodeSize(25);

        when(companyRepository.findForUpdateById(companyId)).thenReturn(Optional.of(company));
        when(shareRepository.findByUserAndCompany(bob, company)).thenReturn(java.util.List.of());
        when(shareRepository.save(any(Share.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shareRepository.findByCompany(company)).thenReturn(java.util.List.of());

        shareService.issueForContribution(c);

        assertThat(company.getTotalUnits()).isEqualTo(1_002_500);
    }
}
