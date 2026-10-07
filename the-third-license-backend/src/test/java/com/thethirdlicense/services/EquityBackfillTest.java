package com.thethirdlicense.services;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.ShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquityBackfillTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private ShareRepository shareRepository;
    @Mock private ContributionRepository contributionRepository;
    @Mock private ShareService shareService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EquityBackfill backfill;

    @Test
    void oldPercentages_becomeUnitsScaledToEarnedLines_thenOwnerGetsFounderStake() {
        User owner = new User("owner", "o@test.com", "x", new HashSet<>());
        Company company = new Company("Legacy", owner);
        Repository_ repo = new Repository_();
        repo.setId(UUID.randomUUID());
        company.addRepository(repo);

        // Old model: Bob 0.6, Carol (bought a share) 0.4 — stored as fractions
        Share bob = new Share();   bob.setPercentage(0.6);
        Share carol = new Share(); carol.setPercentage(0.4);
        when(shareRepository.findByCompany(company)).thenReturn(List.of(bob, carol));

        Contribution approved = new Contribution();
        approved.setApproved(true);
        approved.setModifiedCodeSize(10);
        Contribution pending = new Contribution();
        pending.setModifiedCodeSize(999);
        when(contributionRepository.findByRepositoryId(repo.getId())).thenReturn(List.of(approved, pending));
        when(shareService.getUnitsPerLine()).thenReturn(100L);

        backfill.backfill(company);

        assertThat(bob.getUnits()).isEqualTo(600);     // 0.6 of 10 lines × 100
        assertThat(carol.getUnits()).isEqualTo(400);
        assertThat(company.getTotalUnits()).isEqualTo(1000);
        verify(shareService).issueUnits(company, owner, 1_000_000L);
    }
}
