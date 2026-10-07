package com.thethirdlicense.services;

import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.models.Share;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;
import com.thethirdlicense.repositories.ShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * One-time conversion of companies created under the old percentage-only model (totalUnits == 0)
 * to the unit ledger. Runs at startup and is a no-op once every company has units.
 *
 * Conversion: existing holdings keep their relative sizes (so past trades are preserved) and are
 * scaled to what the company's approved contributions earn under the new rule
 * (lines × units-per-line). The owner then receives the initial founder units.
 */
@Component
public class EquityBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquityBackfill.class);

    private final CompanyRepository companyRepository;
    private final ShareRepository shareRepository;
    private final ContributionRepository contributionRepository;
    private final ShareService shareService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.equity.initial-owner-units:1000000}")
    private long initialOwnerUnits = 1_000_000;

    public EquityBackfill(CompanyRepository companyRepository, ShareRepository shareRepository,
                          ContributionRepository contributionRepository, ShareService shareService,
                          TransactionTemplate transactionTemplate) {
        this.companyRepository = companyRepository;
        this.shareRepository = shareRepository;
        this.contributionRepository = contributionRepository;
        this.shareService = shareService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Company> pending = companyRepository.findByTotalUnits(0);
        for (Company company : pending) {
            try {
                transactionTemplate.executeWithoutResult(status -> backfill(company));
            } catch (RuntimeException e) {
                log.error("Equity backfill failed for company {}", company.getId(), e);
            }
        }
        if (!pending.isEmpty()) {
            log.warn("Converted {} companies to the unit-based equity ledger", pending.size());
        }
    }

    void backfill(Company company) {
        List<Share> shares = shareRepository.findByCompany(company);

        long earnedLines = company.getRepositories().stream()
                .flatMap(repo -> contributionRepository.findByRepositoryId(repo.getId()).stream())
                .filter(Contribution::isApproved)
                .mapToLong(Contribution::getModifiedCodeSize)
                .sum();
        long contributorUnits = earnedLines * shareService.getUnitsPerLine();

        double percentSum = shares.stream().mapToDouble(Share::getPercentage).filter(p -> p > 0).sum();
        long total = 0;
        for (Share share : shares) {
            long units = (percentSum > 0 && share.getPercentage() > 0)
                    ? Math.round(share.getPercentage() / percentSum * contributorUnits)
                    : 0;
            share.setUnits(units);
            total += units;
        }
        shareRepository.saveAll(shares);
        company.setTotalUnits(total);
        companyRepository.save(company);

        // Founder stake, issued last so it dilutes the converted holdings like any other issuance
        shareService.issueUnits(company, company.getOwner(), initialOwnerUnits);
    }
}
