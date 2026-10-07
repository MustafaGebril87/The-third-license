package com.thethirdlicense.services;

import com.thethirdlicense.models.Share;
import org.springframework.transaction.annotation.Transactional;

import com.thethirdlicense.models.User;
import com.thethirdlicense.controllers.ShareDTO;
import com.thethirdlicense.models.Company;
import com.thethirdlicense.models.Contribution;
import com.thethirdlicense.repositories.ShareRepository;
import com.thethirdlicense.repositories.CompanyRepository;
import com.thethirdlicense.repositories.ContributionRepository;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class ShareService {

    private final ShareRepository shareRepository;
    private final CompanyRepository companyRepository;

    /** Units issued per changed line in an approved contribution (owner starts with app.equity.initial-owner-units). */
    @Value("${app.equity.units-per-line:100}")
    private long unitsPerLine = 100;

    @Autowired
    public ShareService(ShareRepository shareRepository, CompanyRepository companyRepository) {
        this.shareRepository = shareRepository;
        this.companyRepository = companyRepository;
    }

    public void offerShare(Share share, User owner) {
        if (!share.getOwner().equals(owner)) {
            throw new AccessDeniedException("You can't offer shares that you don't own.");
        }
        share.setForSale(true);
        shareRepository.save(share);
    }

    public void buyShare(UUID  shareId, User buyer, int amount) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        if (!share.isForSale()) {
            throw new IllegalStateException("This share is not for sale.");
        }

        // Transfer ownership
        share.setOwner(buyer);
        share.setForSale(false);
        shareRepository.save(share);
    }
    /**
     * Issues new ownership units to a user. Everyone else's percentage shrinks proportionally
     * (dilution); nobody's units are ever overwritten. Units land on the user's existing
     * not-for-sale holding, or a new one, so a listing's terms never change mid-sale.
     */
    @Transactional
    public Share issueUnits(Company company, User user, long units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Units to issue must be positive.");
        }
        Company locked = companyRepository.findForUpdateById(company.getId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Share holding = shareRepository.findByUserAndCompany(user, locked).stream()
                .filter(s -> !s.isForSale())
                .findFirst()
                .orElseGet(() -> {
                    Share s = new Share();
                    s.setUser(user);
                    s.setCompany(locked);
                    return s;
                });

        holding.setUnits(holding.getUnits() + units);
        locked.setTotalUnits(locked.getTotalUnits() + units);
        companyRepository.save(locked);
        Share saved = shareRepository.save(holding);

        syncPercentages(locked);
        return saved;
    }

    /** Credits an approved contribution: unitsPerLine units for each line changed. */
    @Transactional
    public void issueForContribution(Contribution contribution) {
        long units = (long) contribution.getModifiedCodeSize() * unitsPerLine;
        if (units > 0) {
            issueUnits(contribution.getRepository().getCompany(), contribution.getUser(), units);
        }
    }

    /** Recomputes the cached percentage of every holding from units / totalUnits. */
    public void syncPercentages(Company company) {
        List<Share> shares = shareRepository.findByCompany(company);
        for (Share share : shares) {
            share.setPercentage(percentOf(share.getUnits(), company.getTotalUnits()));
        }
        shareRepository.saveAll(shares);
    }

    static double percentOf(long units, long totalUnits) {
        return totalUnits <= 0 ? 0.0 : units * 100.0 / totalUnits;
    }

    public long getUnitsPerLine() {
        return unitsPerLine;
    }

    public List<Share> getSharesForUser(User user) {
    	return shareRepository.findByUserId(user.getId());
    }
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.50");   // Stripe minimum charge (USD)
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    private Share findOwnedShare(UUID shareId, UUID requesterId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (requesterId == null || !share.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("You can only manage shares you own.");
        }
        return share;
    }

    /**
     * Splits off part of a holding into a new holding with the same owner (e.g. to sell part of it).
     * splitPercentage is a percentage of the whole company; it's converted to units.
     */
    @Transactional
    public Share splitShare(UUID shareId, double splitPercentage, UUID requesterId) {
        Share original = findOwnedShare(shareId, requesterId);

        if (original.isForSale()) {
            throw new IllegalStateException("Remove the share from sale before splitting it.");
        }
        if (!Double.isFinite(splitPercentage) || splitPercentage <= 0) {
            throw new IllegalArgumentException("Invalid split percentage.");
        }

        long total = original.getCompany().getTotalUnits();
        long splitUnits = Math.round(splitPercentage / 100.0 * total);
        if (splitUnits <= 0 || splitUnits >= original.getUnits()) {
            throw new IllegalArgumentException("Invalid split percentage.");
        }

        original.setUnits(original.getUnits() - splitUnits);
        original.setPercentage(percentOf(original.getUnits(), total));
        shareRepository.save(original);

        Share newShare = new Share();
        newShare.setCompany(original.getCompany());
        newShare.setOwner(original.getOwner());
        newShare.setUnits(splitUnits);
        newShare.setPercentage(percentOf(splitUnits, total));

        return shareRepository.save(newShare);
    }

    public Share markShareForSale(UUID shareId, BigDecimal price, UUID requesterId) {
        Share share = findOwnedShare(shareId, requesterId);

        if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Price must be between $0.50 and $999,999.99.");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Price can have at most 2 decimal places.");
        }
        if (share.getUnits() <= 0) {
            throw new IllegalStateException("This share has no ownership to sell.");
        }
        if (share.getOwner().getStripeAccountId() == null || !share.getOwner().isPayoutsEnabled()) {
            throw new IllegalStateException("Set up payouts (Stripe) before listing shares for sale.");
        }

        share.setForSale(true);
        share.setPrice(price);
        return shareRepository.save(share);
    }

    public List<Share> getAllSharesForSale() {
        return shareRepository.findByIsForSaleTrue();
    }

    public Share unmarkShareForSale(UUID shareId, UUID requesterId) {
        Share share = findOwnedShare(shareId, requesterId);
        share.setForSale(false);
        return shareRepository.save(share);
    }

    public List<Share> getSharesByCompany(UUID companyId) {
        return shareRepository.findByCompanyId(companyId);
    }

    public List<Share> getMarketplaceShares(User currentUser) {
        return shareRepository.findByIsForSaleTrueAndUserIdNot(currentUser.getId());
    }

    
    
}
